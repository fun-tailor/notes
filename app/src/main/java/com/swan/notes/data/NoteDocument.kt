package com.swan.notes.data

import kotlinx.serialization.Serializable

@Serializable
data class NoteDocument(
    val id: String,
    val markdown: String,
    val createdAt: Long,
    val updatedAt: Long,
    val normalOrder: Int = 0,
    val pinnedAt: Long? = null,
    val folderId: String? = null,
    val isStarred: Boolean = false,
    val deletedAt: Long? = null,
)

@Serializable
data class NoteFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class Workspace(
    val version: Int = 1,
    val activeNoteId: String = "",
    val folders: List<NoteFolder> = emptyList(),
    val notes: List<NoteDocument> = emptyList(),
)

enum class NoteSortMode(val value: String) {
    MANUAL("manual"),
    TIME("time");

    companion object {
        fun from(value: String?): NoteSortMode =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}

enum class NoteSortDirection(val value: String) {
    NEWEST("newest"),
    OLDEST("oldest");

    companion object {
        fun from(value: String?): NoteSortDirection =
            entries.firstOrNull { it.value == value } ?: NEWEST
    }
}

sealed interface NoteCategory {
    data object All : NoteCategory
    data object Starred : NoteCategory
    data object Trash : NoteCategory
    data class Folder(val folderId: String) : NoteCategory
}

const val NOTE_TITLE_MAX_LENGTH = 38
const val NOTE_PREVIEW_MAX_LENGTH = 88

fun collapseWhitespace(value: String): String =
    value.replace(Regex("\\s+"), " ").trim()

fun stripMarkdownDecorators(value: String): String = collapseWhitespace(
    value
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^>\\s*"), "")
        .replace(Regex("^[-+*]\\s+"), "")
        .replace(Regex("^\\d+[.)]\\s+"), "")
        .replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("<https?://[^>]+>"), "")
        .replace(Regex("[`*_~]"), ""),
)

private fun truncate(value: String, maxLength: Int): String {
    if (value.length <= maxLength) return value
    return value.substring(0, maxLength - 1).trimEnd() + "…"
}

fun meaningfulLines(markdown: String): List<String> =
    markdown.split(Regex("\\r?\\n"))
        .map(::stripMarkdownDecorators)
        .filter { it.isNotEmpty() }

fun getNoteTitle(markdown: String): String {
    val firstLine = meaningfulLines(markdown).firstOrNull().orEmpty()
    return truncate(firstLine.ifEmpty { "新便签" }, NOTE_TITLE_MAX_LENGTH)
}

fun getNoteListTitle(markdown: String): String {
    val firstLine = markdown.split(Regex("\\r?\\n"))
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotEmpty() && !Regex("^!\\[[^\\]]*\\]\\([^)]*\\)$").matches(line)
        }
    return firstLine ?: "新便签"
}

fun getNotePreview(markdown: String): String {
    val lines = meaningfulLines(markdown)
    val previewLines = if (lines.size > 1) lines.drop(1) else lines
    val text = collapseWhitespace(previewLines.joinToString(" "))
    return truncate(text.ifEmpty { "点击开始记录" }, NOTE_PREVIEW_MAX_LENGTH)
}

fun countMeaningfulChars(markdown: String): Int =
    markdown.count { !it.isWhitespace() }

fun isNotePinned(note: NoteDocument): Boolean = note.pinnedAt != null

fun isNoteDeleted(note: NoteDocument): Boolean = note.deletedAt != null

fun noteCategoryLabel(category: NoteCategory, folders: List<NoteFolder>): String =
    when (category) {
        NoteCategory.All -> "全部便签"
        NoteCategory.Starred -> "加星便签"
        NoteCategory.Trash -> "回收站"
        is NoteCategory.Folder ->
            folders.firstOrNull { it.id == category.folderId }?.name ?: "全部便签"
    }

fun getCategoryNoteDocuments(notes: List<NoteDocument>, category: NoteCategory): List<NoteDocument> =
    when (category) {
        NoteCategory.Trash -> notes.filter(::isNoteDeleted)
        NoteCategory.All -> notes.filter { !isNoteDeleted(it) }
        NoteCategory.Starred -> notes.filter { !isNoteDeleted(it) && it.isStarred }
        is NoteCategory.Folder -> notes.filter { !isNoteDeleted(it) && it.folderId == category.folderId }
    }

fun orderNoteDocuments(
    notes: List<NoteDocument>,
    mode: NoteSortMode = NoteSortMode.MANUAL,
    direction: NoteSortDirection = NoteSortDirection.NEWEST,
): List<NoteDocument> = notes
    .mapIndexed { index, note -> index to note }
    .sortedWith { left, right ->
        val leftNote = left.second
        val rightNote = right.second
        val leftPinned = isNotePinned(leftNote)
        val rightPinned = isNotePinned(rightNote)

        if (leftPinned != rightPinned) {
            return@sortedWith if (leftPinned) -1 else 1
        }

        if (leftPinned && rightPinned) {
            val pinnedDifference = (rightNote.pinnedAt ?: 0L) - (leftNote.pinnedAt ?: 0L)
            if (pinnedDifference != 0L) return@sortedWith pinnedDifference.compareTo(0)
        } else if (mode == NoteSortMode.TIME) {
            val timeDifference = if (direction == NoteSortDirection.NEWEST) {
                rightNote.updatedAt - leftNote.updatedAt
            } else {
                leftNote.updatedAt - rightNote.updatedAt
            }
            if (timeDifference != 0L) return@sortedWith timeDifference.compareTo(0)
        } else {
            val normalDifference = leftNote.normalOrder - rightNote.normalOrder
            if (normalDifference != 0) return@sortedWith normalDifference.compareTo(0)
        }

        left.first - right.first
    }
    .map { it.second }

fun toggleNoteStarred(notes: List<NoteDocument>, noteId: String): List<NoteDocument> =
    notes.map { note ->
        if (note.id == noteId && !isNoteDeleted(note)) note.copy(isStarred = !note.isStarred) else note
    }

fun moveNoteToTrash(notes: List<NoteDocument>, noteId: String, now: Long = System.currentTimeMillis()): List<NoteDocument> =
    notes.map { note ->
        if (note.id == noteId && !isNoteDeleted(note)) {
            note.copy(deletedAt = now, pinnedAt = null)
        } else {
            note
        }
    }

fun restoreNoteFromTrash(notes: List<NoteDocument>, noteId: String): List<NoteDocument> =
    notes.map { note ->
        if (note.id == noteId && isNoteDeleted(note)) {
            note.copy(deletedAt = null, pinnedAt = null)
        } else {
            note
        }
    }

fun moveNoteToFolder(notes: List<NoteDocument>, noteId: String, folderId: String?): List<NoteDocument> =
    notes.map { note ->
        if (note.id == noteId && !isNoteDeleted(note)) note.copy(folderId = folderId) else note
    }

fun toggleNotePinned(notes: List<NoteDocument>, noteId: String, now: Long = System.currentTimeMillis()): List<NoteDocument> {
    val target = notes.firstOrNull { it.id == noteId && !isNoteDeleted(it) } ?: return notes

    if (isNotePinned(target)) {
        return notes.map { note -> if (note.id == noteId) note.copy(pinnedAt = null) else note }
    }

    val newestPinnedAt = notes.maxOfOrNull { it.pinnedAt ?: 0L } ?: 0L
    val nextPinnedAt = maxOf(now, newestPinnedAt + 1)
    return notes.map { note -> if (note.id == noteId) note.copy(pinnedAt = nextPinnedAt) else note }
}

fun nextNormalOrder(notes: List<NoteDocument>): Int =
    (notes.minOfOrNull { it.normalOrder } ?: 0) - 1

fun applyNoteDisplayOrder(notes: List<NoteDocument>, orderedNoteIds: List<String>): List<NoteDocument> {
    val normalNotes = orderNoteDocuments(notes).filter { !isNotePinned(it) }
    val pending = normalNotes.associateBy { it.id }.toMutableMap()
    val ordered = mutableListOf<NoteDocument>()

    for (noteId in orderedNoteIds) {
        pending.remove(noteId)?.let(ordered::add)
    }
    for (note in normalNotes) {
        if (pending.containsKey(note.id)) ordered.add(note)
    }

    val availableOrders = normalNotes.map { it.normalOrder }.sorted()
    val nextOrderById = ordered.mapIndexed { index, note -> note.id to availableOrders[index] }.toMap()

    return notes.map { note ->
        val nextOrder = nextOrderById[note.id]
        if (nextOrder == null || nextOrder == note.normalOrder) note else note.copy(normalOrder = nextOrder)
    }
}

fun createNoteDocument(
    markdown: String = "",
    now: Long = System.currentTimeMillis(),
    normalOrder: Int = 0,
    folderId: String? = null,
    isStarred: Boolean = false,
): NoteDocument = NoteDocument(
    id = java.util.UUID.randomUUID().toString(),
    markdown = markdown,
    createdAt = now,
    updatedAt = now,
    normalOrder = normalOrder,
    folderId = folderId,
    isStarred = isStarred,
)

fun createNoteFolder(name: String, now: Long = System.currentTimeMillis()): NoteFolder =
    NoteFolder(id = java.util.UUID.randomUUID().toString(), name = name.trim(), createdAt = now)
