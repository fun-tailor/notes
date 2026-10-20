package com.swan.notes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceRepository(
    filesDir: File,
    private val scope: CoroutineScope,
) {
    private val store = WorkspaceJson(filesDir)
    private val _workspace = MutableStateFlow(loadInitialWorkspace())
    val workspace: StateFlow<Workspace> = _workspace.asStateFlow()

    @Volatile
    private var dirty = false

    @Volatile
    private var firstDirtyAt = 0L

    @Volatile
    private var lastChangedAt = 0L

    private val writeLock = Any()

    private val writerJob = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
        while (isActive) {
            delay(100)
            val now = System.currentTimeMillis()
            if (!dirty) continue

            val debounceReady = now - lastChangedAt >= SAVE_DEBOUNCE_MS
            val maxWaitReached = now - firstDirtyAt >= SAVE_MAX_WAIT_MS

            if (debounceReady || maxWaitReached) {
                writeNow()
            }
        }
    }

    init {
        writerJob.start()
        if (_workspace.value.notes.isNotEmpty() && _workspace.value.activeNoteId.isBlank()) {
            _workspace.update { it.copy(activeNoteId = it.notes.first().id) }
        }
    }

    private fun loadInitialWorkspace(): Workspace {
        val loaded = store.read()
        if (loaded != null) return loaded
        return Workspace(version = 1, activeNoteId = "", folders = emptyList(), notes = emptyList())
    }

    fun note(noteId: String): NoteDocument? = _workspace.value.notes.firstOrNull { it.id == noteId }

    fun selectNote(noteId: String) {
        if (_workspace.value.activeNoteId == noteId) return
        mutate { it.copy(activeNoteId = noteId) }
    }

    fun createNote(folderId: String?, isStarred: Boolean): NoteDocument {
        val current = _workspace.value
        val note = createNoteDocument(
            now = System.currentTimeMillis(),
            normalOrder = nextNormalOrder(current.notes),
            folderId = folderId,
            isStarred = isStarred,
        )
        mutate { it.copy(activeNoteId = note.id, notes = it.notes + note) }
        return note
    }

    fun updateMarkdown(noteId: String, markdown: String) {
        val current = _workspace.value
        val note = current.notes.firstOrNull { it.id == noteId } ?: return
        if (note.markdown == markdown) return

        val now = System.currentTimeMillis()
        mutate {
            it.copy(
                notes = it.notes.map { item ->
                    if (item.id == noteId) {
                        item.copy(markdown = markdown, updatedAt = now)
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun toggleStarred(noteId: String) = mutate { it.copy(notes = toggleNoteStarred(it.notes, noteId)) }

    fun togglePinned(noteId: String) = mutate { it.copy(notes = toggleNotePinned(it.notes, noteId)) }

    fun moveToTrash(noteId: String) {
        val nextActive = findNextActiveNoteId(noteId, includeTrash = false)
        mutate {
            it.copy(
                notes = moveNoteToTrash(it.notes, noteId),
                activeNoteId = if (it.activeNoteId == noteId) nextActive else it.activeNoteId,
            )
        }
    }

    fun restoreFromTrash(noteId: String) = mutate { it.copy(notes = restoreNoteFromTrash(it.notes, noteId)) }

    fun permanentlyDelete(noteId: String) {
        val nextActive = findNextActiveNoteId(noteId, includeTrash = true)
        mutate {
            it.copy(
                notes = it.notes.filterNot { note -> note.id == noteId },
                activeNoteId = if (it.activeNoteId == noteId) nextActive else it.activeNoteId,
            )
        }
    }

    fun removeNote(noteId: String) {
        permanentlyDelete(noteId)
    }

    fun moveToFolder(noteId: String, folderId: String?) =
        mutate { it.copy(notes = moveNoteToFolder(it.notes, noteId, folderId)) }

    fun createFolder(name: String): NoteFolder? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        _workspace.value.folders.firstOrNull { it.name.trim() == trimmed }?.let { return it }

        val folder = createNoteFolder(trimmed.take(24))
        mutate { it.copy(folders = it.folders + folder) }
        return folder
    }

    fun deleteFolder(folderId: String) {
        mutate {
            it.copy(
                folders = it.folders.filterNot { folder -> folder.id == folderId },
                notes = it.notes.map { note ->
                    if (note.folderId == folderId) note.copy(folderId = null) else note
                },
            )
        }
    }

    fun applyDisplayOrder(orderedNoteIds: List<String>) =
        mutate { it.copy(notes = applyNoteDisplayOrder(it.notes, orderedNoteIds)) }

    fun flush() {
        runBlocking(Dispatchers.IO) {
            if (dirty) writeNow()
        }
    }

    suspend fun flushAsync() {
        withContext(Dispatchers.IO) {
            if (dirty) writeNow()
        }
    }

    private fun findNextActiveNoteId(noteId: String, includeTrash: Boolean): String {
        val notes = _workspace.value.notes
        val targetNote = notes.firstOrNull { it.id == noteId }
        val remaining = notes.filter { it.id != noteId }
        val categoryNotes = if (includeTrash && targetNote?.deletedAt != null) {
            remaining.filter(::isNoteDeleted)
        } else {
            remaining.filter { !isNoteDeleted(it) }
        }
        return orderNoteDocuments(categoryNotes).firstOrNull()?.id.orEmpty()
    }

    private fun mutate(transform: (Workspace) -> Workspace) {
        _workspace.update(transform)
        markDirty()
    }

    private fun markDirty() {
        val now = System.currentTimeMillis()
        if (!dirty) {
            dirty = true
            firstDirtyAt = now
        }
        lastChangedAt = now
    }

    private fun writeNow() {
        synchronized(writeLock) {
            dirty = false
            val snapshot = _workspace.value
            val success = runCatching { store.write(snapshot) }.isSuccess
            if (!success) dirty = true
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 300L
        private const val SAVE_MAX_WAIT_MS = 1_200L
    }
}
