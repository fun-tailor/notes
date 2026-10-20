package com.swan.notes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.swan.notes.AppContainer
import com.swan.notes.data.AppSettings
import com.swan.notes.data.NoteCategory
import com.swan.notes.data.NoteDocument
import com.swan.notes.data.NoteSortDirection
import com.swan.notes.data.NoteSortMode
import com.swan.notes.data.Workspace
import com.swan.notes.data.getCategoryNoteDocuments
import com.swan.notes.data.orderNoteDocuments
import com.swan.notes.data.sync.SafSyncRepository
import com.swan.notes.data.sync.SyncCrypto
import com.swan.notes.data.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { List, Editor }

class NotesViewModel(private val container: AppContainer) : ViewModel() {

    val repository = container.repository
    val settingsStore = container.settingsStore
    val safSync: SafSyncRepository = container.safSync

    val workspace: StateFlow<Workspace> = repository.workspace
    val settings: StateFlow<AppSettings> = settingsStore.settings
    val syncStatus: StateFlow<String> = safSync.status

    private val _screen = MutableStateFlow(Screen.List)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _category = MutableStateFlow<NoteCategory>(NoteCategory.All)
    val category: StateFlow<NoteCategory> = _category.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _categoryOpen = MutableStateFlow(false)
    val categoryOpen: StateFlow<Boolean> = _categoryOpen.asStateFlow()

    private val _editorNoteId = MutableStateFlow<String?>(null)
    val editorNoteId: StateFlow<String?> = _editorNoteId.asStateFlow()

    private var isNewDraft = false

    val isNewDraftEditing: Boolean
        get() = isNewDraft

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun openSettings() {
        _settingsOpen.value = true
        _categoryOpen.value = false
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    fun toggleCategoryOpen() {
        _categoryOpen.value = !_categoryOpen.value
    }

    fun closeCategory() {
        _categoryOpen.value = false
    }

    fun setCategory(category: NoteCategory) {
        _category.value = category
        _categoryOpen.value = false

        val notes = getCategoryNoteDocuments(workspace.value.notes, category)
        val ordered = orderNoteDocuments(notes, settings.value.sortMode, settings.value.sortDirection)
        val activeId = workspace.value.activeNoteId
        if (ordered.none { it.id == activeId }) {
            ordered.firstOrNull()?.let { repository.selectNote(it.id) }
        }
    }

    fun createNote() {
        val category = _category.value
        if (category == NoteCategory.Trash) {
            _category.value = NoteCategory.All
        }
        val folderId = (category as? NoteCategory.Folder)?.folderId
        val star = category == NoteCategory.Starred

        val note = repository.createNote(folderId = folderId, isStarred = star)
        _editorNoteId.value = note.id
        isNewDraft = true
        _categoryOpen.value = false
        _settingsOpen.value = false
        _screen.value = Screen.Editor
    }

    fun openNote(noteId: String) {
        if (repository.note(noteId) == null) return
        repository.selectNote(noteId)
        _editorNoteId.value = noteId
        isNewDraft = false
        _categoryOpen.value = false
        _screen.value = Screen.Editor
    }

    fun closeEditor(markdown: String?) {
        val noteId = _editorNoteId.value
        if (noteId != null && markdown != null) {
            repository.updateMarkdown(noteId, markdown)
        }

        if (noteId != null && isNewDraft) {
            val note = repository.note(noteId)
            if (note != null && note.markdown.trim().isEmpty()) {
                repository.removeNote(noteId)
            }
        }

        isNewDraft = false
        repository.flush()
        _screen.value = Screen.List
        _editorNoteId.value = null
    }

    fun updateMarkdown(noteId: String, markdown: String) {
        repository.updateMarkdown(noteId, markdown)
    }

    fun deleteCurrent(noteId: String) {
        val note = repository.note(noteId) ?: return
        if (note.deletedAt != null) {
            repository.permanentlyDelete(noteId)
        } else {
            repository.moveToTrash(noteId)
        }
        isNewDraft = false
        repository.flush()
        _screen.value = Screen.List
        _editorNoteId.value = null
    }

    fun toggleStarred(noteId: String) = repository.toggleStarred(noteId)

    fun togglePinned(noteId: String) = repository.togglePinned(noteId)

    fun moveToTrash(noteId: String) = repository.moveToTrash(noteId)

    fun restore(noteId: String) = repository.restoreFromTrash(noteId)

    fun permanentlyDelete(noteId: String) = repository.permanentlyDelete(noteId)

    fun createFolder(name: String) = repository.createFolder(name)

    fun deleteFolder(folderId: String) {
        repository.deleteFolder(folderId)
        if ((_category.value as? NoteCategory.Folder)?.folderId == folderId) {
            _category.value = NoteCategory.All
        }
    }

    fun moveNoteToFolder(noteId: String, folderId: String?) =
        repository.moveToFolder(noteId, folderId)

    fun applyDisplayOrder(noteIds: List<String>) {
        repository.applyDisplayOrder(noteIds)
        if (settings.value.sortMode == NoteSortMode.TIME) {
            settingsStore.setSortMode(NoteSortMode.MANUAL)
        }
    }

    fun handleSortButton() {
        if (settings.value.sortMode == NoteSortMode.MANUAL) {
            settingsStore.setSortMode(NoteSortMode.TIME)
            settingsStore.setSortDirection(NoteSortDirection.NEWEST)
        } else {
            settingsStore.setSortDirection(
                if (settings.value.sortDirection == NoteSortDirection.NEWEST) {
                    NoteSortDirection.OLDEST
                } else {
                    NoteSortDirection.NEWEST
                },
            )
        }
    }

    fun setTheme(preference: String) = settingsStore.setTheme(preference)

    fun setSyncDirectory(uri: Uri, name: String?) {
        val salt = SyncCrypto.base64(SyncCrypto.newSalt())
        settingsStore.setSyncDirectory(uri.toString(), name, salt)
        safSync.setPassphrase(null)
    }

    fun disconnectSync() {
        container.appScope.launch { safSync.disconnect() }
    }

    fun setSyncPassphrase(passphrase: String?) {
        safSync.setPassphrase(passphrase)
    }

    fun setEncryptionEnabled(enabled: Boolean, passphrase: String?) {
        if (enabled) {
            safSync.setPassphrase(passphrase)
            val salt = settings.value.syncSalt
                ?: SyncCrypto.base64(SyncCrypto.newSalt())
            settingsStore.setSyncEncryption(true, salt)
        } else {
            settingsStore.setSyncEncryption(false, settings.value.syncSalt)
            safSync.setPassphrase(null)
        }
    }

    suspend fun syncNow(force: Boolean = true): SyncResult {
        val result = safSync.sync(workspace.value, force)
        if (result.ok) {
            safSync.setStatus(result.detailMessage())
        } else {
            safSync.setStatus(result.error ?: "")
        }
        return result
    }

    fun intakeSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val note = repository.createNote(folderId = null, isStarred = false)
        repository.updateMarkdown(note.id, trimmed)
        _category.value = NoteCategory.All
        _searchQuery.value = ""
        _editorNoteId.value = note.id
        isNewDraft = false
        _screen.value = Screen.Editor
    }

    fun onAppStopped() {
        repository.flush()
    }

    fun onBackFromList(): Boolean {
        return when {
            _settingsOpen.value -> {
                _settingsOpen.value = false
                true
            }
            _categoryOpen.value -> {
                _categoryOpen.value = false
                true
            }
            else -> false
        }
    }

    fun noteById(noteId: String): NoteDocument? = repository.note(noteId)
}
