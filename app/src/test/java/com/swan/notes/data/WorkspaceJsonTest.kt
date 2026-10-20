package com.swan.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class WorkspaceJsonTest {

    private fun tempDir() = Files.createTempDirectory("swan-notes-test").toFile()

    private fun workspace(vararg notes: NoteDocument): Workspace = Workspace(
        version = 1,
        activeNoteId = notes.firstOrNull()?.id.orEmpty(),
        folders = listOf(NoteFolder(id = "f1", name = "工作", createdAt = 1)),
        notes = notes.toList(),
    )

    @Test
    fun roundTripKeepsAllFields() {
        val dir = tempDir()
        val store = WorkspaceJson(dir)
        val original = workspace(
            NoteDocument(
                id = "note-1",
                markdown = "第一行\n正文",
                createdAt = 100,
                updatedAt = 200,
                normalOrder = -1,
                pinnedAt = 150,
                folderId = "f1",
                isStarred = true,
            ),
        )

        store.write(original)
        val loaded = store.read()

        assertNotNull(loaded)
        assertEquals(original, loaded)
    }

    @Test
    fun emptyNotesWorkspaceIsAccepted() {
        val dir = tempDir()
        val store = WorkspaceJson(dir)
        val original = Workspace(version = 1, activeNoteId = "", folders = emptyList(), notes = emptyList())

        store.write(original)
        val loaded = store.read()

        assertNotNull(loaded)
        assertEquals(emptyList<NoteDocument>(), loaded!!.notes)
        assertEquals("", loaded.activeNoteId)
    }

    @Test
    fun corruptedMainFileFallsBackToBackup() {
        val dir = tempDir()
        val store = WorkspaceJson(dir)
        val first = workspace(NoteDocument(id = "a", markdown = "一", createdAt = 1, updatedAt = 1))
        val second = workspace(NoteDocument(id = "b", markdown = "二", createdAt = 2, updatedAt = 2))

        store.write(first)
        store.write(second)
        java.io.File(dir, "workspace.json").writeText("{broken", Charsets.UTF_8)

        val loaded = store.read()

        assertNotNull(loaded)
        assertEquals("a", loaded!!.notes.single().id)
    }

    @Test
    fun invalidFolderReferenceIsCleared() {
        val dir = tempDir()
        val store = WorkspaceJson(dir)
        val original = workspace(
            NoteDocument(
                id = "a",
                markdown = "一",
                createdAt = 1,
                updatedAt = 1,
                folderId = "missing",
            ),
        )

        store.write(original)
        val loaded = store.read()

        assertNotNull(loaded)
        assertNull(loaded!!.notes.single().folderId)
    }

    @Test
    fun duplicateNoteIdsAreRejected() {
        val dir = tempDir()
        val store = WorkspaceJson(dir)
        val duplicateJson = """
            {
              "version": 1,
              "activeNoteId": "a",
              "folders": [],
              "notes": [
                {"id":"a","markdown":"一","createdAt":1,"updatedAt":1,"normalOrder":0},
                {"id":"a","markdown":"二","createdAt":2,"updatedAt":2,"normalOrder":-1}
              ]
            }
        """.trimIndent()

        java.io.File(dir, "workspace.json").writeText(duplicateJson, Charsets.UTF_8)

        assertNull(store.read())
    }
}
