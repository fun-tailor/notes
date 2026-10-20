package com.swan.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentTest {

    private fun note(
        id: String,
        order: Int = 0,
        updatedAt: Long = 0,
        pinnedAt: Long? = null,
        folderId: String? = null,
        starred: Boolean = false,
        deletedAt: Long? = null,
        markdown: String = "",
    ) = NoteDocument(
        id = id,
        markdown = markdown,
        createdAt = 0,
        updatedAt = updatedAt,
        normalOrder = order,
        pinnedAt = pinnedAt,
        folderId = folderId,
        isStarred = starred,
        deletedAt = deletedAt,
    )

    @Test
    fun manualSortPutsPinnedFirstThenNormalOrder() {
        val notes = listOf(
            note("a", order = 2),
            note("p1", order = 9, pinnedAt = 10),
            note("b", order = 0),
            note("p2", order = 8, pinnedAt = 20),
        )

        val ordered = orderNoteDocuments(notes, NoteSortMode.MANUAL, NoteSortDirection.NEWEST)

        assertEquals(listOf("p2", "p1", "b", "a"), ordered.map { it.id })
    }

    @Test
    fun timeSortHonorsDirection() {
        val notes = listOf(
            note("old", updatedAt = 100),
            note("new", updatedAt = 300),
            note("middle", updatedAt = 200),
        )

        assertEquals(
            listOf("new", "middle", "old"),
            orderNoteDocuments(notes, NoteSortMode.TIME, NoteSortDirection.NEWEST).map { it.id },
        )
        assertEquals(
            listOf("old", "middle", "new"),
            orderNoteDocuments(notes, NoteSortMode.TIME, NoteSortDirection.OLDEST).map { it.id },
        )
    }

    @Test
    fun equalKeysKeepOriginalArrayOrder() {
        val notes = listOf(
            note("a", order = 0),
            note("b", order = 0),
            note("c", order = 0),
        )
        assertEquals(
            listOf("a", "b", "c"),
            orderNoteDocuments(notes, NoteSortMode.MANUAL, NoteSortDirection.NEWEST).map { it.id },
        )
    }

    @Test
    fun trashClearsPinnedAndRestoreKeepsItCleared() {
        val notes = listOf(note("a", pinnedAt = 5))
        val trashed = moveNoteToTrash(notes, "a", now = 10)
        assertNull(trashed.first().pinnedAt)
        assertEquals(10L, trashed.first().deletedAt)

        val restored = restoreNoteFromTrash(trashed, "a")
        assertNull(restored.first().deletedAt)
        assertNull(restored.first().pinnedAt)
    }

    @Test
    fun categoryFiltersExcludeTrashExceptTrashView() {
        val folder = NoteFolder(id = "f1", name = "工作", createdAt = 0)
        val notes = listOf(
            note("active", folderId = "f1", starred = true),
            note("trashed", deletedAt = 1, starred = true),
        )

        assertEquals(listOf("active"), getCategoryNoteDocuments(notes, NoteCategory.All).map { it.id })
        assertEquals(listOf("active"), getCategoryNoteDocuments(notes, NoteCategory.Starred).map { it.id })
        assertEquals(listOf("active"), getCategoryNoteDocuments(notes, NoteCategory.Folder("f1")).map { it.id })
        assertEquals(listOf("trashed"), getCategoryNoteDocuments(notes, NoteCategory.Trash).map { it.id })
        assertEquals("工作", noteCategoryLabel(NoteCategory.Folder("f1"), listOf(folder)))
    }

    @Test
    fun displayOrderReusesExistingSlotsWithoutCollisions() {
        val notes = listOf(
            note("a", order = -3),
            note("b", order = -2),
            note("c", order = -1),
        )

        val reordered = applyNoteDisplayOrder(notes, listOf("c", "a", "b"))

        assertEquals(listOf(-3, -2, -1), reordered.map { it.normalOrder }.sorted())
        assertEquals(
            listOf("c", "a", "b"),
            reordered.sortedBy { it.normalOrder }.map { it.id },
        )
    }

    @Test
    fun titlesUseFirstNonEmptyLineAndSkipImageOnlyLines() {
        assertEquals("新便签", getNoteListTitle(""))
        assertEquals("新便签", getNoteListTitle("\n  \n"))
        assertEquals("新便签", getNoteListTitle("![图](x)"))
        assertEquals("正文", getNoteListTitle("![图](x)\n正文"))
        assertEquals("# 标题", getNoteListTitle("# 标题\n正文"))
        assertEquals("标题", getNoteTitle("# 标题\n正文"))
        assertEquals("a b", getNoteTitle("  a   b  "))
    }

    @Test
    fun nextNormalOrderStartsAtMinusOne() {
        assertEquals(-1, nextNormalOrder(emptyList()))
        assertEquals(-4, nextNormalOrder(listOf(note("a", order = -3), note("b", order = -1))))
    }

    @Test
    fun charCountIgnoresWhitespace() {
        assertEquals(3, countMeaningfulChars("a b\n\tc"))
        assertTrue(getNoteTitle("a".repeat(60)).endsWith("…"))
        assertEquals(NOTE_TITLE_MAX_LENGTH, getNoteTitle("a".repeat(60)).length)
    }
}

