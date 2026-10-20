package com.swan.notes.data.sync

import com.swan.notes.data.NoteDocument
import com.swan.notes.data.getNoteTitle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SYNC_FORMAT_NAME = "smartisan-notes-sync"
const val SYNC_FORMAT_VERSION = 1
const val SYNC_MANIFEST_FILE_NAME = "notes.index.json"
const val SYNC_PBKDF2_ITERATIONS = 150_000

@Serializable
data class SyncKdf(
    val name: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = SYNC_PBKDF2_ITERATIONS,
    val salt: String,
)

@Serializable
data class SyncEncryption(
    val enabled: Boolean,
    val cipher: String? = null,
    val kdf: SyncKdf? = null,
    val keyCheck: String? = null,
)

@Serializable
data class SyncManifestNote(
    val id: String,
    val file: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val starred: Boolean,
    val pinned: Boolean,
    val folder: String,
    val deleted: Boolean,
    val size: Int,
    val sha256: String,
)

@Serializable
data class SyncManifest(
    val format: String = SYNC_FORMAT_NAME,
    val version: Int = SYNC_FORMAT_VERSION,
    val generator: String = "开源版锤子便签",
    val generatedAt: String,
    val encryption: SyncEncryption,
    val notes: List<SyncManifestNote>,
)

object SyncFormat {

    @OptIn(ExperimentalSerializationApi::class)
    private val manifestJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
    }

    private val mirrorFilePattern =
        Regex("""[^/\\]+\.md(?: \(\d+\))?(?:\.txt)?(?:\.enc(?: \(\d+\))?(?:\.txt)?)?""")

    fun escapeYamlString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun serializeNote(note: NoteDocument): String {
        val frontMatter = buildString {
            append("---\n")
            append("id: ").append(note.id).append('\n')
            append("title: \"").append(escapeYamlString(getNoteTitle(note.markdown))).append("\"\n")
            append("createdAt: ").append(note.createdAt).append('\n')
            append("updatedAt: ").append(note.updatedAt).append('\n')
            append("starred: ").append(note.isStarred).append('\n')
            append("pinned: ").append(note.pinnedAt != null).append('\n')
            append("folder: ").append(note.folderId ?: "").append('\n')
            append("deleted: ").append(note.deletedAt != null).append('\n')
            append("---\n\n")
        }
        return frontMatter + note.markdown
    }

    fun noteFileName(noteId: String, encrypted: Boolean): String =
        "$noteId.md" + if (encrypted) ".enc" else ""

    fun isMirrorFile(fileName: String): Boolean = mirrorFilePattern.matches(fileName)

    fun buildManifest(manifest: SyncManifest): String =
        manifestJson.encodeToString(SyncManifest.serializer(), manifest) + "\n"

    data class ParsedNoteFile(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val starred: Boolean,
        val pinned: Boolean,
        val folder: String,
        val deleted: Boolean,
        val markdown: String,
    )

    fun parseNoteFile(text: String): ParsedNoteFile? {
        if (!text.startsWith("---\n")) return null
        val end = text.indexOf("\n---\n", startIndex = 4)
        if (end < 0) return null

        val header = text.substring(4, end)
        val markdown = text.substring(end + 5).removePrefix("\n")
        val values = mutableMapOf<String, String>()

        for (line in header.split("\n")) {
            val separator = line.indexOf(':', startIndex = 0)
            if (separator <= 0) continue
            values[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }

        val id = values["id"]?.takeIf { it.isNotEmpty() } ?: return null
        val title = values["title"]?.removeSurrounding("\"")?.let(::unescapeYamlString).orEmpty()

        return ParsedNoteFile(
            id = id,
            title = title,
            createdAt = values["createdAt"]?.toLongOrNull() ?: 0L,
            updatedAt = values["updatedAt"]?.toLongOrNull() ?: 0L,
            starred = values["starred"] == "true",
            pinned = values["pinned"] == "true",
            folder = values["folder"].orEmpty(),
            deleted = values["deleted"] == "true",
            markdown = markdown,
        )
    }

    fun unescapeYamlString(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")
}

