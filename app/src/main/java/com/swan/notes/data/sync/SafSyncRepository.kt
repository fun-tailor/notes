package com.swan.notes.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swan.notes.data.SettingsStore
import com.swan.notes.data.Workspace
import com.swan.notes.data.getNoteTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.SecretKey

data class SyncResult(
    val written: Int = 0,
    val deleted: Int = 0,
    val unchanged: Int = 0,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null

    fun pullMessage(): String = when {
        error != null -> error
        written > 0 -> "已同步 $written 条便签"
        else -> "没有需要同步的便签"
    }

    fun detailMessage(): String = when {
        error != null -> error
        else -> "已同步：写入 $written，跳过 $unchanged，移除 $deleted"
    }

    fun mirrorMessage(): String = when {
        error != null -> error
        written == 0 && deleted == 0 -> ""
        deleted > 0 -> "已镜像 $written 条变更，移除 $deleted 个文件"
        else -> "已镜像 $written 条变更"
    }
}

@Serializable
private data class SyncCacheEntry(
    val name: String = "",
    val hash: String = "",
)

@Serializable
private data class SyncCache(
    val treeUri: String = "",
    val manifestHash: String = "",
    val entries: Map<String, SyncCacheEntry> = emptyMap(),
)

private data class CachedKey(
    val salt: String,
    val passphrase: String,
    val key: SecretKey,
    val keyCheck: String,
)

class SafSyncRepository(
    private val context: Context,
    filesDir: File,
    private val settingsStore: SettingsStore,
) {
    private val mutex = Mutex()
    private val cacheFile = File(filesDir, "saf-cache.json")
    private val cacheJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    private var passphrase: String? = null

    @Volatile
    private var cachedKey: CachedKey? = null

    fun setPassphrase(value: String?) {
        passphrase = value?.takeIf { it.isNotEmpty() }
        cachedKey = null
    }

    fun hasPassphrase(): Boolean = !passphrase.isNullOrEmpty()

    fun setStatus(message: String) {
        _status.value = message
    }

    @OptIn(FlowPreview::class)
    fun startAutoMirror(scope: CoroutineScope, workspaceFlow: kotlinx.coroutines.flow.Flow<Workspace>) {
        scope.launch {
            workspaceFlow
                .map { it.copy(activeNoteId = "") }
                .distinctUntilChanged()
                .debounce(650)
                .collect { workspace ->
                    if (settingsStore.settings.value.syncTreeUri.isNullOrBlank()) return@collect
                    if (
                        settingsStore.settings.value.syncEncryptionEnabled &&
                        passphrase.isNullOrEmpty()
                    ) {
                        return@collect
                    }

                    val result = sync(workspace, force = false)
                    val message = result.mirrorMessage()
                    if (message.isNotEmpty()) _status.value = message
                }
        }
    }

    suspend fun sync(workspace: Workspace, force: Boolean): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { runSync(workspace, force) }
                .getOrElse { error ->
                    SyncResult(error = readableError(error))
                }
        }
    }

    suspend fun disconnect() {
        cacheFile.delete()
        setPassphrase(null)
        settingsStore.setSyncDirectory(uri = null, name = null, salt = null)
    }

    private fun runSync(workspace: Workspace, force: Boolean): SyncResult {
        val settings = settingsStore.settings.value
        val treeUriString = settings.syncTreeUri
            ?: return SyncResult(error = "尚未选择同步文件夹")

        val treeUri = Uri.parse(treeUriString)
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: return SyncResult(error = "无法打开同步文件夹")

        if (!directory.exists()) return SyncResult(error = "同步文件夹已不存在")
        if (!directory.canWrite()) return SyncResult(error = "同步文件夹不可写，请重新选择")

        val encrypted = settings.syncEncryptionEnabled
        val sessionPassphrase = passphrase

        if (encrypted && sessionPassphrase.isNullOrEmpty()) {
            return SyncResult(error = "请输入同步口令后再同步")
        }

        val salt = if (encrypted) {
            resolveSalt(settings.syncSalt)
        } else {
            ByteArray(16)
        }

        val key = if (encrypted) {
            deriveKey(salt, sessionPassphrase.orEmpty())
        } else {
            null
        }

        val previousCache = readCache().takeIf { it.treeUri == treeUriString } ?: SyncCache(treeUri = treeUriString)
        val existingFiles = directory.listFiles().mapNotNull { file ->
            file.name?.let { it to file }
        }.toMap()
        val existingNames = existingFiles.keys

        val nextEntries = mutableMapOf<String, SyncCacheEntry>()
        val records = mutableListOf<Record>()
        val pending = mutableListOf<Pending>()
        var unchanged = 0

        for (note in workspace.notes) {
            val serialized = SyncFormat.serializeNote(note)
            val hash = sha256Hex(serialized)
            val fileName = SyncFormat.noteFileName(note.id, encrypted)
            val previousEntry = previousCache.entries[fileName]
            val knownName = previousEntry?.name?.takeIf { it.isNotEmpty() } ?: fileName

            nextEntries[fileName] = SyncCacheEntry(name = knownName, hash = hash)
            records += Record(note, fileName, hash, serialized)

            val isOnDisk = existingNames.contains(knownName) || existingNames.contains(fileName)
            if (!force && previousEntry?.hash == hash && isOnDisk) {
                unchanged += 1
                continue
            }

            pending += Pending(fileName, serialized)
        }

        var written = 0
        for (item in pending) {
            val contents = if (encrypted) {
                SyncCrypto.encryptText(item.plain, key!!, salt)
            } else {
                item.plain
            }
            val actualName = writeMirrorFile(directory, existingFiles, item.fileName, contents)
                ?: return SyncResult(error = "无法写入 ${item.fileName}")
            nextEntries[item.fileName] = (nextEntries[item.fileName] ?: SyncCacheEntry()).copy(name = actualName)
            written += 1
        }

        val manifestNotes = records.map { record ->
            SyncManifestNote(
                id = record.note.id,
                file = nextEntries[record.fileName]?.name?.takeIf { it.isNotEmpty() } ?: record.fileName,
                title = getNoteTitle(record.note.markdown),
                createdAt = record.note.createdAt,
                updatedAt = record.note.updatedAt,
                starred = record.note.isStarred,
                pinned = record.note.pinnedAt != null,
                folder = record.note.folderId ?: "",
                deleted = record.note.deletedAt != null,
                size = record.serialized.toByteArray(Charsets.UTF_8).size,
                sha256 = record.hash,
            )
        }

        val encryption = if (encrypted) {
            SyncEncryption(
                enabled = true,
                cipher = "AES-256-GCM",
                kdf = SyncKdf(salt = SyncCrypto.base64(salt)),
                keyCheck = cachedKey?.keyCheck ?: SyncCrypto.keyCheck(key!!),
            )
        } else {
            SyncEncryption(enabled = false)
        }

        val manifest = SyncManifest(
            generatedAt = Instant.now().toString(),
            encryption = encryption,
            notes = manifestNotes,
        )
        val manifestText = SyncFormat.buildManifest(manifest)
        val manifestHash = sha256Hex(manifestText)

        if (manifestHash != previousCache.manifestHash) {
            existingFiles[SYNC_MANIFEST_FILE_NAME]?.let { runCatching { it.delete() } }
            writeDocument(directory, SYNC_MANIFEST_FILE_NAME, manifestText, "application/json")
                ?: return SyncResult(error = "无法写入 notes.index.json")
        }

        val desiredNames = records.map { record ->
            nextEntries[record.fileName]?.name?.takeIf { it.isNotEmpty() } ?: record.fileName
        }.toSet()
        val ownedNames = nextEntries.values.map { it.name }.filter { it.isNotEmpty() }.toSet()
        var deleted = 0

        for ((name, file) in existingFiles) {
            if (name == SYNC_MANIFEST_FILE_NAME) continue
            if (!SyncFormat.isMirrorFile(name)) continue
            if (name in desiredNames) continue
            if (name in ownedNames) continue
            if (runCatching { file.delete() }.getOrDefault(false)) deleted += 1
        }

        writeCache(
            SyncCache(
                treeUri = treeUriString,
                manifestHash = manifestHash,
                entries = nextEntries,
            ),
        )

        return SyncResult(written = written, deleted = deleted, unchanged = unchanged)
    }

    private fun resolveSalt(storedSalt: String?): ByteArray {
        val decoded = storedSalt?.let {
            runCatching { SyncCrypto.fromBase64(it) }.getOrNull()
        }
        val salt = decoded?.takeIf { it.size == 16 } ?: SyncCrypto.newSalt()

        if (storedSalt != SyncCrypto.base64(salt)) {
            settingsStore.setSyncEncryption(
                settingsStore.settings.value.syncEncryptionEnabled,
                SyncCrypto.base64(salt),
            )
        }

        return salt
    }

    private fun deriveKey(salt: ByteArray, passphrase: String): SecretKey {
        val saltText = SyncCrypto.base64(salt)
        cachedKey?.let { cached ->
            if (cached.salt == saltText && cached.passphrase == passphrase) return cached.key
        }

        val key = SyncCrypto.deriveKey(passphrase, salt)
        cachedKey = CachedKey(
            salt = saltText,
            passphrase = passphrase,
            key = key,
            keyCheck = SyncCrypto.keyCheck(key),
        )
        return key
    }

    private fun writeMirrorFile(
        directory: DocumentFile,
        existingFiles: Map<String, DocumentFile>,
        name: String,
        contents: String,
    ): String? {
        existingFiles[name]?.let { runCatching { it.delete() } }
        return writeDocument(directory, name, contents, mimeTypeFor(name))
    }

    private fun writeDocument(
        directory: DocumentFile,
        name: String,
        contents: String,
        mimeType: String,
    ): String? {
        val created = runCatching { directory.createFile(mimeType, name) }.getOrNull() ?: return null
        if (created.name != name) {
            runCatching { created.renameTo(name) }
        }

        val actualName = created.name?.takeIf { it.isNotEmpty() } ?: name
        val written = runCatching {
            context.contentResolver.openOutputStream(created.uri, "wt")?.use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("无法打开输出流")
        }.isSuccess

        return if (written) actualName else null
    }

    private fun mimeTypeFor(name: String): String = when {
        name.endsWith(".json") -> "application/json"
        name.endsWith(".enc") -> "application/octet-stream"
        else -> "text/plain"
    }

    private fun readCache(): SyncCache {
        if (!cacheFile.exists()) return SyncCache()
        return runCatching {
            cacheJson.decodeFromString(SyncCache.serializer(), cacheFile.readText(Charsets.UTF_8))
        }.getOrDefault(SyncCache())
    }

    private fun writeCache(cache: SyncCache) {
        runCatching {
            cacheFile.writeText(cacheJson.encodeToString(SyncCache.serializer(), cache), Charsets.UTF_8)
        }
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun readableError(error: Throwable): String = when (error) {
        is SecurityException -> "没有同步文件夹的访问权限，请重新选择"
        is IllegalArgumentException -> error.message ?: "同步参数错误"
        else -> error.message ?: "同步失败"
    }

    private data class Record(
        val note: com.swan.notes.data.NoteDocument,
        val fileName: String,
        val hash: String,
        val serialized: String,
    )

    private data class Pending(
        val fileName: String,
        val plain: String,
    )
}
