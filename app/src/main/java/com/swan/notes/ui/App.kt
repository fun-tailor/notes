package com.swan.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swan.notes.AppContainer
import com.swan.notes.ui.editor.EditorScreen
import com.swan.notes.ui.list.NoteListScreen
import com.swan.notes.ui.settings.SettingsScreen
import com.swan.notes.ui.theme.SwanTheme

@Composable
fun SwanNotesApp(
    container: AppContainer,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
) {
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotesViewModel(container) as T
            }
        }
    }
    val viewModel: NotesViewModel = viewModel(factory = factory)

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val editorNoteId by viewModel.editorNoteId.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val categoryOpen by viewModel.categoryOpen.collectAsStateWithLifecycle()

    val dark = settings.isDarkTheme(isSystemInDarkTheme())

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.intakeSharedText(sharedText)
            onSharedTextConsumed()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAppStopped()
    }

    SwanTheme(darkTheme = dark) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                Screen.List -> NoteListScreen(viewModel = viewModel)
                Screen.Editor -> {
                    val noteId = editorNoteId
                    if (noteId != null) {
                        EditorScreen(
                            viewModel = viewModel,
                            noteId = noteId,
                            autoFocus = viewModel.isNewDraftEditing,
                        )
                    } else {
                        NoteListScreen(viewModel = viewModel)
                    }
                }
            }

            if (settingsOpen) {
                SettingsScreen(
                    viewModel = viewModel,
                    onClose = viewModel::closeSettings,
                )
            }
        }

        BackHandler(enabled = !settingsOpen && categoryOpen) {
            viewModel.closeCategory()
        }
    }
}
