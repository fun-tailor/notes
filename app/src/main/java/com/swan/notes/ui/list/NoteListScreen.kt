package com.swan.notes.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan.notes.R
import com.swan.notes.data.NoteCategory
import com.swan.notes.data.NoteDocument
import com.swan.notes.data.NoteFolder
import com.swan.notes.data.NoteSortDirection
import com.swan.notes.data.getCategoryNoteDocuments
import com.swan.notes.data.getNoteListTitle
import com.swan.notes.data.getNoteTitle
import com.swan.notes.data.isNoteDeleted
import com.swan.notes.data.isNotePinned
import com.swan.notes.data.noteCategoryLabel
import com.swan.notes.data.orderNoteDocuments
import com.swan.notes.data.sync.SyncResult
import com.swan.notes.ui.NotesViewModel
import com.swan.notes.ui.common.ClipImage
import com.swan.notes.ui.common.EmptyState
import com.swan.notes.ui.common.PaperSlice
import com.swan.notes.ui.common.PngIconButton
import com.swan.notes.ui.common.SearchMagnifier
import com.swan.notes.ui.theme.SwanTheme
import com.swan.notes.ui.common.TopBarContainer
import com.swan.notes.ui.common.TopBarRow
import com.swan.notes.ui.common.formatNoteDate
import com.swan.notes.ui.category.CategoryOverlay
import com.swan.notes.ui.theme.SwanColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val RowHeight = 75.3333.dp
private val SwipeOpenWidth = 66.dp
private val SwipeOpenThreshold = 28.dp
private val PullTrigger = 96.dp
private val PullMax = 168.dp
private val PullHold = 52.dp

private enum class PullPhase { Idle, Pulling, Ready, Syncing, Done }

@Composable
fun NoteListScreen(viewModel: NotesViewModel) {
    val colors = SwanTheme.colors
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryOpen by viewModel.categoryOpen.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val categoryNotes = remember(workspace.notes, category) {
        getCategoryNoteDocuments(workspace.notes, category)
    }
    val orderedNotes = remember(categoryNotes, settings.sortMode, settings.sortDirection) {
        orderNoteDocuments(categoryNotes, settings.sortMode, settings.sortDirection)
    }
    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredNotes = remember(orderedNotes, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            orderedNotes
        } else {
            orderedNotes.filter { note ->
                (getNoteTitle(note.markdown) + " " + note.markdown)
                    .lowercase()
                    .contains(normalizedQuery)
            }
        }
    }

    val listTitles = remember(filteredNotes) {
        filteredNotes.associate { it.id to getNoteListTitle(it.markdown) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var openSwipeId by remember { mutableStateOf<String?>(null) }

    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }

    var pullOffset by remember { mutableFloatStateOf(0f) }
    var pullRaw by remember { mutableFloatStateOf(0f) }
    var pullPhase by remember { mutableStateOf(PullPhase.Idle) }
    var pullMessage by remember { mutableStateOf("") }

    val rowHeightPx = with(density) { RowHeight.toPx() }
    val triggerPx = with(density) { PullTrigger.toPx() }
    val maxPullPx = with(density) { PullMax.toPx() }
    val holdPx = with(density) { PullHold.toPx() }

    LaunchedEffect(category) {
        listState.scrollToItem(0)
        openSwipeId = null
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && pullPhase == PullPhase.Idle) {
            openSwipeId = null
        }
    }

    fun settlePull(target: Float) {
        scope.launch {
            animate(
                initialValue = pullOffset,
                targetValue = target,
                animationSpec = tween(180),
            ) { value, _ -> pullOffset = value }
        }
    }

    fun runPullSync() {
        if (pullPhase == PullPhase.Syncing) return
        pullPhase = PullPhase.Syncing
        pullMessage = "正在同步..."
        pullRaw = 0f
        settlePull(holdPx)

        scope.launch {
            val hasDirectory = !settings.syncTreeUri.isNullOrBlank()
            val result: SyncResult? = if (hasDirectory) viewModel.syncNow(force = true) else null
            pullMessage = when {
                !hasDirectory -> "先在设置里选择同步文件夹"
                result == null -> "同步失败"
                result.error == "尚未选择同步文件夹" -> "先在设置里选择同步文件夹"
                else -> result.pullMessage()
            }
            pullPhase = PullPhase.Done
            delay(1200)
            pullPhase = PullPhase.Idle
            pullMessage = ""
            pullOffset = 0f
            pullRaw = 0f
        }
    }

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0 && pullOffset > 0f && pullPhase == PullPhase.Idle) {
                    val next = (pullOffset + available.y).coerceAtLeast(0f)
                    val consumed = next - pullOffset
                    pullOffset = next
                    pullRaw = next / 0.52f
                    pullPhase = if (pullOffset <= 0f) PullPhase.Idle else PullPhase.Pulling
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (pullPhase != PullPhase.Idle && pullPhase != PullPhase.Pulling && pullPhase != PullPhase.Ready) {
                    return Offset.Zero
                }
                if (available.y <= 0f) return Offset.Zero
                if (listState.firstVisibleItemIndex > 0) return Offset.Zero
                if (listState.firstVisibleItemScrollOffset > 0) return Offset.Zero

                pullRaw += available.y
                pullOffset = (pullRaw * 0.52f).coerceIn(0f, maxPullPx)
                pullPhase = if (pullOffset >= triggerPx) PullPhase.Ready else PullPhase.Pulling
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset <= 0f) return Velocity.Zero
                val shouldSync = pullOffset >= triggerPx
                if (shouldSync) {
                    runPullSync()
                } else {
                    pullPhase = PullPhase.Idle
                    settlePull(0f)
                    pullRaw = 0f
                }
                return Velocity(0f, available.y)
            }
        }
    }

    val pinnedCount = filteredNotes.count { isNotePinned(it) }
    val noteMap = remember(workspace.notes) { workspace.notes.associateBy { it.id } }
    val displayNotes: List<NoteDocument> = dragOrder
        ?.mapNotNull { noteMap[it] }
        ?: filteredNotes
    val dragFromIndex = displayNotes.indexOfFirst { it.id == draggingId }
    val dragToIndex = if (dragFromIndex >= 0) {
        (dragFromIndex + (dragOffset / rowHeightPx).roundToInt())
            .coerceIn(pinnedCount, displayNotes.lastIndex)
    } else {
        -1
    }

    fun startDrag(noteId: String) {
        if (searchQuery.isNotBlank()) return
        val note = noteMap[noteId] ?: return
        if (isNotePinned(note) || isNoteDeleted(note)) return
        dragOrder = displayNotes.map { it.id }
        draggingId = noteId
        dragOffset = 0f
        dragMoved = false
        openSwipeId = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun updateDrag(noteId: String, deltaY: Float) {
        val order = dragOrder ?: return
        val from = order.indexOf(noteId)
        if (from < 0) return

        val minOffset = (pinnedCount - from) * rowHeightPx
        val maxOffset = (order.lastIndex - from) * rowHeightPx
        dragOffset = (dragOffset + deltaY).coerceIn(minOffset, maxOffset)
        if (abs(deltaY) > 0.5f) dragMoved = true
    }

    fun endDrag() {
        val order = dragOrder
        val noteId = draggingId
        if (dragMoved && order != null && noteId != null) {
            val from = order.indexOf(noteId)
            val target = if (from >= 0) {
                (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(pinnedCount, order.lastIndex)
            } else {
                from
            }
            if (from >= 0 && target != from) {
                val mutable = order.toMutableList()
                mutable.removeAt(from)
                mutable.add(target, noteId)
                val nonPinnedIds = mutable.filter { id -> noteMap[id]?.let { !isNotePinned(it) } == true }
                viewModel.applyDisplayOrder(nonPinnedIds)
            }
        }
        dragOrder = null
        draggingId = null
        dragOffset = 0f
        dragMoved = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        com.swan.notes.ui.common.DimBackground(colors.dark)

        Column(modifier = Modifier.fillMaxSize()) {
            TopBarContainer {
                TopBarRow {
                    Box(modifier = Modifier.width(100.dp)) {
                        PngIconButton(
                            resourceId = if (colors.dark) R.drawable.btn_settings_dark else R.drawable.btn_settings,
                            contentDescription = "设置",
                            onClick = viewModel::openSettings,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = viewModel::toggleCategoryOpen,
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = noteCategoryLabel(category, workspace.folders),
                            color = colors.onTopBar,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFF453D3B),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 1f,
                                ),
                            ),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "▾",
                            color = colors.onTopBarMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }

                    PngIconButton(
                        resourceId = if (colors.dark) R.drawable.btn_sort_down_dark else R.drawable.btn_sort_down,
                        contentDescription = "排序",
                        onClick = viewModel::handleSortButton,
                        flipVertical = settings.sortDirection == NoteSortDirection.OLDEST,
                    )

                    PngIconButton(
                        resourceId = if (colors.dark) R.drawable.btn_create_dark else R.drawable.btn_create,
                        contentDescription = "新建便签",
                        onClick = viewModel::createNote,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PullIndicator(
                    phase = pullPhase,
                    offset = pullOffset,
                    message = pullMessage,
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, pullOffset.roundToInt()) }
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item(key = "search") {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                        )
                    }

                    if (displayNotes.isEmpty()) {
                        item(key = "empty") {
                            EmptyListState(
                                query = searchQuery,
                                isTrash = category == NoteCategory.Trash,
                                onClearSearch = { viewModel.setSearchQuery("") },
                            )
                        }
                    } else {
                        itemsIndexed(displayNotes, key = { _, note -> note.id }) { index, note ->
                            val isDragging = draggingId == note.id
                            val dragShift = when {
                                isDragging -> dragOffset
                                dragFromIndex < dragToIndex && index in (dragFromIndex + 1)..dragToIndex -> -rowHeightPx
                                dragToIndex < dragFromIndex && index in dragToIndex until dragFromIndex -> rowHeightPx
                                else -> 0f
                            }
                            NoteRow(
                                note = note,
                                title = listTitles[note.id] ?: getNoteListTitle(note.markdown),
                                isTrash = category == NoteCategory.Trash,
                                isOpen = openSwipeId == note.id,
                                isSelected = draggingId == note.id,
                                isAnyDragging = draggingId != null,
                                isDragging = isDragging,
                                dragShift = dragShift,
                                dragEnabled = searchQuery.isBlank() && !isNotePinned(note) && !isNoteDeleted(note),
                                swipeEnabled = category != NoteCategory.Trash,
                                onTap = {
                                    if (openSwipeId != null) {
                                        openSwipeId = null
                                    } else {
                                        viewModel.openNote(note.id)
                                    }
                                },
                                onSwipeSettled = { open ->
                                    openSwipeId = if (open) note.id else null
                                },
                                onDragStart = { startDrag(note.id) },
                                onDrag = { dy -> updateDrag(note.id, dy) },
                                onDragEnd = { endDrag() },
                                onToggleStar = { viewModel.toggleStarred(note.id) },
                                onTogglePin = { viewModel.togglePinned(note.id) },
                                onRestore = { viewModel.restore(note.id) },
                                onPermanentDelete = {
                                    viewModel.permanentlyDelete(note.id)
                                },
                                onRequestDelete = {
                                    openSwipeId = null
                                    viewModel.moveToTrash(note.id)
                                },
                                modifier = Modifier
                                    .zIndex(if (isDragging) 10f else 0f),
                            )
                        }
                    }
                }
            }
        }

        FloatingCreateButton(
            onClick = viewModel::createNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 20.dp),
        )

        AnimatedVisibility(
            visible = categoryOpen,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.985f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                animationSpec = tween(180),
            ),
            exit = fadeOut(tween(120)) + scaleOut(
                targetScale = 0.985f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                animationSpec = tween(140),
            ),
        ) {
            CategoryOverlay(
                folders = workspace.folders,
                notes = workspace.notes,
                category = category,
                sortDirection = settings.folderSortDirection,
                onSelect = viewModel::setCategory,
                onCreateFolder = { viewModel.createFolder(it) },
                onDeleteFolder = viewModel::deleteFolder,
                onToggleSortDirection = {
                    viewModel.settingsStore.setFolderSortDirection(
                        if (settings.folderSortDirection == "asc") "desc" else "asc",
                    )
                },
                onClose = viewModel::closeCategory,
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = SwanTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 3.dp)
            .height(31.3333.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    val topShadow = if (colors.dark) Color(0x66000000) else Color(0x335C3C2A)
                    val sideShadow = if (colors.dark) Color(0x40000000) else Color(0x1F5C3C2A)
                    drawRoundRect(color = colors.searchBackground, cornerRadius = radius)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to topShadow,
                            0.55f to topShadow.copy(alpha = topShadow.alpha * 0.65f),
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = 2.5.dp.toPx(),
                        ),
                        cornerRadius = radius,
                    )
                    clipRect(bottom = size.height * 0.52f) {
                        val sideDepth = 2.dp.toPx()
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0f to sideShadow,
                                0.55f to sideShadow.copy(alpha = sideShadow.alpha * 0.6f),
                                1f to Color.Transparent,
                                startX = 0f,
                                endX = sideDepth,
                            ),
                            cornerRadius = radius,
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0f to sideShadow,
                                0.55f to sideShadow.copy(alpha = sideShadow.alpha * 0.6f),
                                1f to Color.Transparent,
                                startX = size.width,
                                endX = size.width - sideDepth,
                            ),
                            cornerRadius = radius,
                        )
                    }
                    drawRoundRect(
                        color = colors.searchHint.copy(alpha = 0.18f),
                        cornerRadius = radius,
                        style = Stroke(width = 0.6667.dp.toPx()),
                    )
                }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchMagnifier(
                tint = colors.searchIcon,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "快速搜索关键字",
                        color = colors.searchHint,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = colors.searchText, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.caret),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { onQueryChange("") },
                        )
                        .semantics { contentDescription = "清除搜索" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        color = colors.searchHint,
                        fontSize = 17.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteDocument,
    title: String,
    isTrash: Boolean,
    isOpen: Boolean,
    isSelected: Boolean,
    isAnyDragging: Boolean,
    isDragging: Boolean,
    dragShift: Float,
    dragEnabled: Boolean,
    swipeEnabled: Boolean,
    onTap: () -> Unit,
    onSwipeSettled: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwanTheme.colors
    val density = LocalDensity.current
    val maxSwipe = with(density) { SwipeOpenWidth.toPx() }
    val threshold = with(density) { SwipeOpenThreshold.toPx() }
    val scope = rememberCoroutineScope()
    var swipeX by remember(note.id) { mutableFloatStateOf(if (isOpen) maxSwipe else 0f) }

    LaunchedEffect(isOpen) {
        animate(
            initialValue = swipeX,
            targetValue = if (isOpen) maxSwipe else 0f,
            animationSpec = tween(180),
        ) { value, _ -> swipeX = value }
    }

    Box(modifier = modifier.fillMaxWidth().height(RowHeight)) {
        if (!isTrash && swipeX > 0.5f) {
            SwipeDeleteButton(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 22.dp),
                onClick = onRequestDelete,
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = swipeX
                    translationY = dragShift
                    if (isDragging) {
                        alpha = 0.92f
                    }
                }
                .pointerInput(note.id, dragEnabled, swipeEnabled, isOpen) {
                    val slop = 6.dp.toPx()
                    val dragSlop = 12.dp.toPx()
                    val longPressMs = 220L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var mode = 0 // 0 pending, 1 swipe, 2 pass, 3 drag
                        var total = Offset.Zero
                        var currentOffset = swipeX
                        val startOffset = currentOffset
                        val startedAt = System.currentTimeMillis()

                        while (true) {
                            if (
                                mode == 0 &&
                                dragEnabled &&
                                total.getDistance() < dragSlop &&
                                System.currentTimeMillis() - startedAt >= longPressMs
                            ) {
                                mode = 3
                                onDragStart()
                            }

                            val event = if (mode == 0) {
                                val remaining = longPressMs - (System.currentTimeMillis() - startedAt)
                                withTimeoutOrNull(remaining.coerceAtLeast(1L)) {
                                    awaitPointerEvent(PointerEventPass.Main)
                                }
                            } else {
                                awaitPointerEvent(PointerEventPass.Main)
                            } ?: continue

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.isConsumed && mode == 0) {
                                mode = 2
                                break
                            }

                            val delta = change.positionChange()
                            total += delta

                            if (mode == 0) {
                                if (total.getDistance() >= slop) {
                                    val horizontal = abs(total.x) > abs(total.y) * 1.15f
                                    if (swipeEnabled && horizontal) {
                                        mode = 1
                                    } else if (total.getDistance() >= dragSlop) {
                                        mode = 2
                                        break
                                    }
                                }
                            }

                            if (mode == 1) {
                                change.consume()
                                currentOffset = (startOffset + total.x).coerceIn(0f, maxSwipe)
                                swipeX = currentOffset
                            } else if (mode == 3) {
                                change.consume()
                                onDrag(delta.y)
                            }

                            if (change.changedToUpIgnoreConsumed()) {
                                when (mode) {
                                    1 -> {
                                        val shouldOpen = currentOffset >= threshold
                                        onSwipeSettled(shouldOpen)
                                        if (shouldOpen == isOpen) {
                                            scope.launch {
                                                animate(
                                                    initialValue = swipeX,
                                                    targetValue = if (shouldOpen) maxSwipe else 0f,
                                                    animationSpec = tween(180),
                                                ) { value, _ -> swipeX = value }
                                            }
                                        }
                                    }
                                    3 -> {
                                        onDragEnd()
                                    }
                                    else -> {
                                        if (!change.isConsumed && total.getDistance() < slop * 2) {
                                            onTap()
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            PaperSlice(pressed = isSelected, modifier = Modifier.matchParentSize())

            Text(
                text = formatNoteDate(note.updatedAt),
                color = colors.listTime,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 52.dp, top = 5.dp)
                    .height(24.dp),
            )

            Text(
                text = title,
                color = colors.listTitle,
                fontSize = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 52.dp, end = 85.dp, top = 38.5.dp)
                    .height(30.dp),
            )

            if (!isTrash) {
                Image(
                    painter = painterResource(
                        when {
                            isNotePinned(note) && colors.dark -> R.drawable.icon_top_checked_dark
                            isNotePinned(note) -> R.drawable.icon_top_checked
                            colors.dark -> R.drawable.icon_top_normal_dark
                            else -> R.drawable.icon_top_normal
                        },
                    ),
                    contentDescription = if (isNotePinned(note)) "取消置顶" else "置顶便签",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-43).dp)
                        .size(width = 39.dp, height = 47.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onTogglePin,
                        ),
                )

                Image(
                    painter = painterResource(
                        when {
                            note.isStarred && colors.dark -> R.drawable.note_item_star_fav_dark
                            note.isStarred -> R.drawable.note_item_star_fav
                            colors.dark -> R.drawable.note_item_star_invalid_dark
                            else -> R.drawable.note_item_star_invalid
                        },
                    ),
                    contentDescription = if (note.isStarred) "取消加星" else "加星便签",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(width = 39.dp, height = 47.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onToggleStar,
                        ),
                )
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 22.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TrashAction("恢复", onRestore)
                    TrashAction("永久删除", onPermanentDelete)
                }
            }
        }

        ClipImage(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(28.6667.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = if (isAnyDragging || swipeX > 0.5f) -19.dp.toPx() else 0f
                },
        )
    }
}

@Composable
private fun TrashAction(label: String, onClick: () -> Unit) {
    val colors = SwanTheme.colors
    val background = if (colors.dark) colors.searchBackground else Color(0xE0FFFCF6)
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.listTitle.copy(alpha = 0.64f), fontSize = 12.sp)
    }
}

@Composable
private fun SwipeDeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = SwanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(width = 73.6667.dp, height = 35.6667.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                when {
                    colors.dark && pressed -> R.drawable.btn_slide_delete_pressed_dark
                    colors.dark -> R.drawable.btn_slide_delete_normal_dark
                    pressed -> R.drawable.btn_slide_delete_pressed
                    else -> R.drawable.btn_slide_delete_normal
                },
            ),
            contentDescription = "删除便签",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun EmptyListState(
    query: String,
    isTrash: Boolean,
    onClearSearch: () -> Unit,
) {
    val colors = SwanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = when {
            query.isNotBlank() -> "没有匹配的便签"
            isTrash -> "回收站为空"
            else -> "这个分类还没有便签"
        }
        Text(text = text, color = colors.metaText, fontSize = 14.sp)
        if (query.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.searchBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClearSearch,
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "清除搜索", color = colors.listTitle, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FloatingCreateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwanTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "fabScale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            //.shadow(6.dp, CircleShape) //会出现 8边形
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.fabBackground)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "新建便签" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val stroke = 2.2.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(size.width / 2f, 3.dp.toPx()),
                end = Offset(size.width / 2f, size.height - 3.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(3.dp.toPx(), size.height / 2f),
                end = Offset(size.width - 3.dp.toPx(), size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}


private const val PULL_SYNC_ICON_PATH =
    "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"

private val PullSyncIconPath: Path by lazy {
    PathParser()
        .parsePathString(PULL_SYNC_ICON_PATH)
        .toPath()
}

@Composable
private fun BoxScope.PullIndicator(
    phase: PullPhase,
    offset: Float,
    message: String,
) {
    if (phase == PullPhase.Idle && offset <= 0f) return

    val colors = SwanTheme.colors
    val density = LocalDensity.current
    val triggerPx = with(density) { PullTrigger.toPx() }
    val progress = (offset / triggerPx).coerceIn(0f, 1f)

    val infinite = rememberInfiniteTransition(label = "pullSync")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "pullSpin",
    )

    val label = when {
        phase == PullPhase.Syncing -> "正在同步..."
        phase == PullPhase.Done -> message
        phase == PullPhase.Ready -> "松开即可同步..."
        offset > 0f -> "下拉即可同步..."
        else -> ""
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(with(density) { offset.coerceAtLeast(0f).toDp() })
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                modifier = Modifier
                    .size(20.dp)
                    .rotate(
                        if (phase == PullPhase.Syncing) {
                            spin
                        } else {
                            progress * 180f
                        }
                    )
            ) {
                val iconScale = minOf(size.width, size.height) / 24f

                scale(
                    scaleX = iconScale,
                    scaleY = iconScale,
                    pivot = Offset.Zero
                ) {
                    drawPath(
                        path = PullSyncIconPath,
                        color = Color(0xFF8D7355)
                    )
                }
            }

            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = colors.metaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

