package com.swan.notes.data.sync

import com.swan.notes.data.NoteDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFormatTest {

    private fun note(markdown: String) = NoteDocument(
        id = "note-1",
        markdown = markdown,
        createdAt = 1,
        updatedAt = 2,
        normalOrder = -1,
        pinnedAt = 3,
        folderId = "folder-1",
        isStarred = true,
    )

    @Test
    fun serializedNoteContainsFrontMatterAndBody() {
        val text = SyncFormat.serializeNote(note("正文\n第二行"))

        assertTrue(text.startsWith("---\n"))
        assertTrue(text.contains("id: note-1\n"))
        assertTrue(text.contains("starred: true\n"))
        assertTrue(text.contains("pinned: true\n"))
        assertTrue(text.contains("folder: folder-1\n"))
        assertTrue(text.endsWith("---\n\n正文\n第二行"))
    }

    @Test
    fun titleIsEscapedAndUnescaped() {
        val text = SyncFormat.serializeNote(note("# 标题 \"引号\" \\ 反斜杠"))
        assertTrue(text.contains("title: \"标题 \\\"引号\\\" \\\\ 反斜杠\""))

        val parsed = SyncFormat.parseNoteFile(text)
        assertNotNull(parsed)
        assertEquals("标题 \"引号\" \\ 反斜杠", parsed!!.title)
        assertEquals("# 标题 \"引号\" \\ 反斜杠", SyncFormat.parseNoteFile(text)!!.markdown)
    }

    @Test
    fun longTitleIsTruncatedTo38Chars() {
        val text = SyncFormat.serializeNote(note("字".repeat(60)))
        val title = SyncFormat.parseNoteFile(text)!!.title
        assertEquals(38, title.length)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun emptyTitleBecomesNewNote() {
        val parsed = SyncFormat.parseNoteFile(SyncFormat.serializeNote(note("   \n\n")))
        assertEquals("新便签", parsed!!.title)
    }

    @Test
    fun fileNamesAndMirrorPattern() {
        assertEquals("abc.md", SyncFormat.noteFileName("abc", encrypted = false))
        assertEquals("abc.md.enc", SyncFormat.noteFileName("abc", encrypted = true))

        assertTrue(SyncFormat.isMirrorFile("abc.md"))
        assertTrue(SyncFormat.isMirrorFile("abc.md.enc"))
        assertTrue(SyncFormat.isMirrorFile("abc.md (1).txt"))
        assertTrue(SyncFormat.isMirrorFile("abc.md.enc (2).txt"))
        assertFalse(SyncFormat.isMirrorFile("notes.index.json"))
        assertFalse(SyncFormat.isMirrorFile("assets/abc.md"))
        assertFalse(SyncFormat.isMirrorFile("abc.txt"))
    }

    @Test
    fun manifestUsesExpectedFormatAndIndentation() {
        val manifest = SyncManifest(
            generatedAt = "2026-10-20T00:00:00Z",
            encryption = SyncEncryption(enabled = false),
            notes = listOf(
                SyncManifestNote(
                    id = "note-1",
                    file = "note-1.md",
                    title = "标题",
                    createdAt = 1,
                    updatedAt = 2,
                    starred = true,
                    pinned = false,
                    folder = "",
                    deleted = false,
                    size = 10,
                    sha256 = "abc",
                ),
            ),
        )

        val text = SyncFormat.buildManifest(manifest)

        assertTrue(text.startsWith("{\n  \"format\": \"smartisan-notes-sync\",\n"))
        assertTrue(text.contains("\n  \"version\": 1,\n"))
        assertTrue(text.contains("\"generator\": \"开源版锤子便签\""))
        assertTrue(text.endsWith("\n"))
        assertFalse(text.contains("\"cipher\""))
    }

    @Test
    fun malformedFilesReturNull() {
        assertEquals(null, SyncFormat.parseNoteFile("no front matter"))
        assertEquals(null, SyncFormat.parseNoteFile("---\nid: x\n---"))
    }
}

