package com.swan.notes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class AppSettings(
    @SerialName("notes.previewTheme")
    val themePreference: String = "default",
    @SerialName("notes.listSortMode")
    val listSortMode: String = "manual",
    @SerialName("notes.listSortDirection")
    val listSortDirection: String = "newest",
    @SerialName("notes.syncTreeUri")
    val syncTreeUri: String? = null,
    @SerialName("notes.syncDirectoryName")
    val syncDirectoryName: String? = null,
    @SerialName("notes.syncEncryptionEnabled")
    val syncEncryptionEnabled: Boolean = false,
    @SerialName("notes.safEncryptionSalt.v1")
    val syncSalt: String? = null,
    @SerialName("notes.folderSortDirection")
    val folderSortDirection: String = "asc",
) {
    val sortMode: NoteSortMode get() = NoteSortMode.from(listSortMode)
    val sortDirection: NoteSortDirection get() = NoteSortDirection.from(listSortDirection)

    fun isDarkTheme(systemDark: Boolean): Boolean = when (themePreference) {
        "system" -> systemDark
        "smartisan-dark" -> true
        else -> false
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_DEFAULT = "default"
        const val THEME_DARK = "smartisan-dark"
    }
}

@OptIn(ExperimentalSerializationApi::class)
class SettingsStore(
    filesDir: File,
    private val scope: CoroutineScope,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }

    private val file = File(filesDir, FILE_NAME)
    private val tempFile = File(filesDir, "$FILE_NAME.tmp")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val saveRequests = MutableSharedFlow<AppSettings>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch(Dispatchers.IO) {
            saveRequests.collectLatest { settings ->
                kotlinx.coroutines.delay(120)
                write(settings)
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        saveRequests.tryEmit(next)
    }

    fun setTheme(preference: String) = update { it.copy(themePreference = preference) }

    fun setSortMode(mode: NoteSortMode) = update { it.copy(listSortMode = mode.value) }

    fun setSortDirection(direction: NoteSortDirection) =
        update { it.copy(listSortDirection = direction.value) }

    fun setFolderSortDirection(direction: String) =
        update { it.copy(folderSortDirection = direction) }

    fun setSyncDirectory(uri: String?, name: String?, salt: String?) = update {
        it.copy(syncTreeUri = uri, syncDirectoryName = name, syncSalt = salt)
    }

    fun setSyncEncryption(enabled: Boolean, salt: String?) = update {
        it.copy(syncEncryptionEnabled = enabled, syncSalt = salt ?: it.syncSalt)
    }

    private fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrDefault(AppSettings())
    }

    private fun write(settings: AppSettings) {
        runCatching {
            val payload = json.encodeToString(AppSettings.serializer(), settings)
            FileOutputStream(tempFile).use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

    companion object {
        const val FILE_NAME = "settings.json"
    }
}

