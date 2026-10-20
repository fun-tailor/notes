package com.swan.notes.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@OptIn(ExperimentalSerializationApi::class)
class WorkspaceJson(private val filesDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val mainFile = File(filesDir, FILE_NAME)
    private val backupFile = File(filesDir, "$FILE_NAME.bak")
    private val tempFile = File(filesDir, "$FILE_NAME.tmp")

    fun read(): Workspace? {
        readWorkspace(mainFile)?.let { return it }

        if (mainFile.exists()) {
            val corrupt = File(filesDir, "$FILE_NAME.corrupt")
            if (!corrupt.exists()) {
                runCatching { mainFile.copyTo(corrupt, overwrite = false) }
            }
        }

        return readWorkspace(backupFile)
    }

    private fun readWorkspace(file: File): Workspace? {
        if (!file.exists()) return null

        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val parsed = json.decodeFromString(Workspace.serializer(), raw)
            normalizeWorkspace(parsed)
        }.getOrNull()
    }

    fun write(workspace: Workspace) {
        val payload = json.encodeToString(Workspace.serializer(), workspace)

        FileOutputStream(tempFile).use { stream ->
            stream.write(payload.toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }

        if (mainFile.exists()) {
            Files.move(
                mainFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        Files.move(
            tempFile.toPath(),
            mainFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun normalizeWorkspace(workspace: Workspace): Workspace? {
        if (workspace.version != 1) return null

        val noteIds = workspace.notes.map { it.id }
        if (noteIds.any { it.isBlank() }) return null
        if (noteIds.toSet().size != noteIds.size) return null

        val seenFolderIds = mutableSetOf<String>()
        val folders = workspace.folders.filter { folder ->
            folder.id.isNotBlank() &&
                folder.name.isNotBlank() &&
                seenFolderIds.add(folder.id)
        }
        val validFolderIds = folders.map { it.id }.toSet()

        val notes = workspace.notes.map { note ->
            note.copy(folderId = note.folderId?.takeIf { it in validFolderIds })
        }

        val activeNoteId = when {
            notes.any { it.id == workspace.activeNoteId } -> workspace.activeNoteId
            notes.isNotEmpty() -> notes.first().id
            else -> ""
        }

        return Workspace(
            version = 1,
            activeNoteId = activeNoteId,
            folders = folders,
            notes = notes,
        )
    }

    companion object {
        const val FILE_NAME = "workspace.json"
    }
}

