package com.swan.notes.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan.notes.R
import com.swan.notes.data.NoteCategory
import com.swan.notes.data.NoteDocument
import com.swan.notes.data.NoteFolder
import com.swan.notes.data.countMeaningfulChars
import com.swan.notes.data.isNoteDeleted
import com.swan.notes.data.noteCategoryLabel
import com.swan.notes.ui.NotesViewModel
import com.swan.notes.ui.common.DialogButton
import com.swan.notes.ui.common.ListSheetRow
import com.swan.notes.ui.common.PngIconButton
import com.swan.notes.ui.common.SwanBottomSheet
import com.swan.notes.ui.common.SwanTextField
import com.swan.notes.ui.theme.SwanTheme
import com.swan.notes.ui.common.TopBarContainer
import com.swan.notes.ui.common.TopBarRow
import com.swan.notes.ui.common.formatNoteDate
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val EditorLineHeight = 51.sp
private val GutterWidth = 25.dp

@OptIn(ExperimentalTextApi::class, FlowPreview::class)
@Composable
fun EditorScreen(
    viewModel: NotesViewModel,
    noteId: String,
    autoFocus: Boolean,
) {
    val colors = SwanTheme.colors
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val note = workspace.notes.firstOrNull { it.id == noteId }
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val scrollState = rememberScrollState()

    val noteState = remember(noteId) { TextFieldState(note?.markdown ?: "", TextRange(0)) }

    var folderSheetOpen by remember { mutableStateOf(false) }
    var shareSheetOpen by remember { mutableStateOf(false) }
    var imageDialogOpen by remember { mutableStateOf(false) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var isEditorFocused by remember { mutableStateOf(false) }
    var caretRect by remember { mutableStateOf<Rect?>(null) }
    var lastAutoScrollOffset by remember(noteId) { mutableIntStateOf(-1) }
    var lastAutoScrollViewport by remember(noteId) { mutableIntStateOf(-1) }

    val isDeleted = note?.let(::isNoteDeleted) == true
    val lineHeightPx = with(density) { EditorLineHeight.toPx() }
    val lineHeightDp = with(density) { lineHeightPx.toDp() }
    val metaBarHeightPx = lineHeightPx
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val charCount by produceState(
        initialValue = countMeaningfulChars(noteState.text.toString()),
        noteId,
    ) {
        snapshotFlow { noteState.text.toString() }
            .debounce(100)
            .collect { value = countMeaningfulChars(it) }
    }

    fun currentMarkdown(): String = noteState.text.toString()

    fun finish() {
        viewModel.closeEditor(if (isDeleted) null else currentMarkdown())
    }

    fun insertImageLink(url: String, alt: String) {
        val selection = noteState.selection
        val insertAt = selection.min
        if (url.isBlank()) {
            noteState.edit {
                replace(selection.min, selection.max, "![]()")
                this.selection = TextRange(insertAt + 4)
            }
        } else {
            val snippet = "![${alt.trim()}](${url.trim()})"
            noteState.edit {
                replace(selection.min, selection.max, snippet)
                this.selection = TextRange(insertAt + snippet.length)
            }
        }
    }

    BackHandler(enabled = true) { finish() }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (viewModel.noteById(noteId)?.let { !isNoteDeleted(it) } == true) {
            viewModel.updateMarkdown(noteId, currentMarkdown())
            viewModel.repository.flush()
        }
    }

    LaunchedEffect(noteId, isDeleted) {
        if (isDeleted) return@LaunchedEffect
        snapshotFlow { noteState.text.toString() }
            .debounce(300)
            .collect { text -> viewModel.updateMarkdown(noteId, text) }
    }

    DisposableEffect(Unit) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(noteId) {
        scrollState.scrollTo(0)
        caretRect = null
        lastAutoScrollOffset = -1
        lastAutoScrollViewport = -1
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus && !isDeleted) {
            delay(120)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(isEditorFocused, viewportHeightPx) {
        if (!isEditorFocused || viewportHeightPx <= 0) return@LaunchedEffect
        val rect = caretRect ?: return@LaunchedEffect
        val edge = with(density) { 4.dp.toPx() }
        val cursorTop = metaBarHeightPx + rect.top
        val cursorBottom = metaBarHeightPx + rect.bottom
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = visibleTop + viewportHeightPx
        when {
            cursorBottom > visibleBottom - edge -> {
                val target = (cursorBottom - viewportHeightPx + edge)
                    .roundToInt()
                    .coerceIn(scrollState.value, scrollState.maxValue)
                if (target > scrollState.value) scrollState.scrollTo(target)
            }
            cursorTop < visibleTop + edge -> {
                val target = (cursorTop - edge)
                    .roundToInt()
                    .coerceIn(0, scrollState.value)
                if (target < scrollState.value) scrollState.scrollTo(target)
            }
        }
    }

    if (note == null) {
        LaunchedEffect(Unit) { viewModel.closeEditor(null) }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.editorPaper)
            .imePadding(),
    ) {
        TopBarContainer {
            TopBarRow {
                PngIconButton(
                    resourceId = if (colors.dark) R.drawable.btn_back_dark else R.drawable.btn_back,
                    contentDescription = "返回",
                    onClick = { finish() },
                )
                Spacer(Modifier.weight(1f))

                if (isDeleted) {
                    TopBarTextButton(text = "恢复", onClick = { viewModel.restore(noteId) })
                    TopBarTextButton(text = "永久删除", onClick = { viewModel.deleteCurrent(noteId) })
                } else {
                    EditorOverflowMenu(
                        expanded = topMenuOpen,
                        onExpandedChange = { topMenuOpen = it },
                        onInsertImage = {
                            topMenuOpen = false
                            imageDialogOpen = true
                        },
                        onDelete = {
                            topMenuOpen = false
                            viewModel.deleteCurrent(noteId)
                        },
                        onShare = {
                            topMenuOpen = false
                            shareSheetOpen = true
                        },
                    )
                    PngIconButton(
                        resourceId = if (colors.dark) R.drawable.btn_save_notes_dark else R.drawable.btn_save_notes,
                        contentDescription = "完成",
                        iconSize = 30.dp,
                        onClick = { finish() },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Box(modifier = Modifier.matchParentSize().background(colors.editorPaper))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                EditorMetaBar(
                    note = note,
                    folders = workspace.folders,
                    charCount = charCount,
                    height = lineHeightDp,
                    ruleColor = colors.editorRule,
                    folderEnabled = !isDeleted,
                    onFolderClick = { if (!isDeleted) folderSheetOpen = true },
                )

                val minTextHeight = with(density) {
                    val remaining = (viewportHeightPx - metaBarHeightPx).coerceAtLeast(0f).toDp()
                    maxOf(lineHeightDp * 3 + 24.dp, remaining)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTextHeight)
                        .drawPaperRules(lineHeightPx, colors.editorRule),
                ) {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = colors.caret,
                            backgroundColor = colors.selection,
                        ),
                    ) {
                        BasicTextField(
                            state = noteState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 39.dp, end = 18.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    isEditorFocused = it.isFocused
                                    if (!it.isFocused) caretRect = null
                                },
                            enabled = !isDeleted,
                            readOnly = isDeleted,
                            textStyle = TextStyle(
                                color = colors.editorText,
                                fontSize = 16.sp,
                                lineHeight = EditorLineHeight,
                                fontFamily = FontFamily.SansSerif,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None,
                                ),
                            ),
                            keyboardOptions = KeyboardOptions.Default,
                            lineLimits = TextFieldLineLimits.MultiLine(
                                minHeightInLines = 3,
                                maxHeightInLines = Int.MAX_VALUE,
                            ),
                            onTextLayout = lbl@{ getResult ->
                                val layout = getResult() ?: return@lbl
                                val offset = noteState.selection.max
                                    .coerceIn(0, layout.layoutInput.text.length)
                                val rect = layout.getCursorRect(offset)
                                caretRect = rect
                                if (!isEditorFocused) {
                                    lastAutoScrollOffset = -1
                                    return@lbl
                                }
                                if (offset == lastAutoScrollOffset && viewportHeightPx == lastAutoScrollViewport) {
                                    return@lbl
                                }
                                lastAutoScrollOffset = offset
                                lastAutoScrollViewport = viewportHeightPx

                                val cursorTop = metaBarHeightPx + rect.top
                                val cursorBottom = metaBarHeightPx + rect.bottom
                                val edge = 4.dp.toPx()
                                val visibleTop = scrollState.value.toFloat()
                                val visibleBottom = visibleTop + viewportHeightPx

                                if (cursorBottom > visibleBottom - edge) {
                                    val target = (cursorBottom - viewportHeightPx + edge)
                                        .roundToInt()
                                        .coerceIn(0, scrollState.maxValue)
                                    if (target > scrollState.value) {
                                        scope.launch { scrollState.scrollTo(target) }
                                    }
                                } else if (cursorTop < visibleTop + edge) {
                                    val target = (cursorTop - edge)
                                        .roundToInt()
                                        .coerceAtLeast(0)
                                    if (target < scrollState.value) {
                                        scope.launch { scrollState.scrollTo(target) }
                                    }
                                }
                            },
                            cursorBrush = SolidColor(Color.Transparent),
                        )
                    }

                    EditorCaret(
                        caretRect = caretRect,
                        visible = isEditorFocused && noteState.selection.collapsed,
                        color = colors.caret,
                    )
                }
            }

            GutterOverlay(
                periodPx = lineHeightPx,
                scrollOffset = scrollState.value,
                ruleColor = colors.editorRule,
            )
        }
    }

    if (folderSheetOpen) {
        FolderPickerSheet(
            folders = workspace.folders,
            currentFolderId = note.folderId,
            onSelect = { folderId ->
                folderSheetOpen = false
                viewModel.moveNoteToFolder(noteId, folderId)
            },
            onDismiss = { folderSheetOpen = false },
        )
    }

    if (shareSheetOpen) {
        ShareSheet(
            markdown = currentMarkdown(),
            onDismiss = { shareSheetOpen = false },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("便签", currentMarkdown()))
                shareSheetOpen = false
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                shareSheetOpen = false
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentMarkdown())
                }
                context.startActivity(Intent.createChooser(intent, "分享便签"))
            },
        )
    }

    if (imageDialogOpen) {
        ImageLinkDialog(
            onDismiss = { imageDialogOpen = false },
            onConfirm = { url, alt ->
                imageDialogOpen = false
                insertImageLink(url, alt)
            },
        )
    }
}

@Composable
private fun GutterOverlay(
    periodPx: Float,
    scrollOffset: Int,
    ruleColor: Color,
) {
    val colors = SwanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(GutterWidth)
            .background(colors.gutterBackground)
            .drawBehind {
                if (periodPx <= 0f) return@drawBehind
                val stroke = 0.6667.dp.toPx()
                var index = ceil(scrollOffset / periodPx).toInt().coerceAtLeast(1)
                var y = index * periodPx - scrollOffset
                while (y <= size.height) {
                    drawLine(
                        color = ruleColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                    index += 1
                    y += periodPx
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.gutterBorder),
        )
    }
}

private fun Modifier.drawPaperRules(periodPx: Float, ruleColor: Color): Modifier = this.then(
    Modifier.drawBehind {
        if (periodPx <= 0f) return@drawBehind
        val stroke = 0.6667.dp.toPx()
        var y = periodPx
        while (y <= size.height + periodPx) {
            drawLine(
                color = ruleColor,
                start = Offset(GutterWidth.toPx(), y),
                end = Offset(size.width, y),
                strokeWidth = stroke,
            )
            y += periodPx
        }
    },
)

@Composable
private fun EditorMetaBar(
    note: NoteDocument,
    folders: List<NoteFolder>,
    charCount: Int,
    height: Dp,
    ruleColor: Color,
    folderEnabled: Boolean,
    onFolderClick: () -> Unit,
) {
    val colors = SwanTheme.colors
    val folderLabel = noteCategoryLabel(
        note.folderId?.let { NoteCategory.Folder(it) } ?: NoteCategory.All,
        folders,
    )
    val stroke = 0.6667.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.metaBackground)
            .drawBehind {
                drawLine(
                    color = ruleColor,
                    start = Offset(GutterWidth.toPx(), size.height - stroke.toPx() / 2f),
                    end = Offset(size.width, size.height - stroke.toPx() / 2f),
                    strokeWidth = stroke.toPx(),
                )
            }
            .padding(start = 35.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.folderBackground)
                .border(0.6667.dp, colors.folderBorder, RoundedCornerShape(4.dp))
                .then(
                    if (folderEnabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onFolderClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = "▧", color = colors.folderText.copy(alpha = 0.6f), fontSize = 10.sp)
            Text(
                text = folderLabel,
                color = colors.folderText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Text(text = "▾", color = colors.folderText.copy(alpha = 0.6f), fontSize = 9.sp)
        }

        Text(
            text = "${formatNoteDate(note.updatedAt)} | $charCount",
            color = colors.metaText,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EditorCaret(
    caretRect: Rect?,
    visible: Boolean,
    color: Color,
) {
    if (!visible || caretRect == null) return

    val blink by rememberInfiniteTransition(label = "caretBlink").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretBlinkAlpha",
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 39.dp, end = 18.dp),
    ) {
        val height = 20.sp.toPx()
        val top = caretRect.center.y - height / 2f
        drawRoundRect(
            color = color.copy(alpha = if (blink > 0.5f) 1f else 0f),
            topLeft = Offset(caretRect.left, top),
            size = Size(2.dp.toPx(), height),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
    }
}

@Composable
private fun EditorOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInsertImage: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = SwanTheme.colors
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onExpandedChange(true) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "…",
                color = colors.onTopBar,
                fontSize = 30.sp,
                lineHeight = 24.sp,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = colors.dialogBackground,
        ) {
            DropdownMenuItem(
                text = { Text("插入图片链接", color = colors.ink, fontSize = 14.sp) },
                onClick = onInsertImage,
            )
            DropdownMenuItem(
                text = { Text("分享", color = colors.ink, fontSize = 14.sp) },
                onClick = onShare,
            )
            DropdownMenuItem(
                text = { Text("删除", color = colors.ink, fontSize = 14.sp) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun TopBarTextButton(text: String, onClick: () -> Unit) {
    val colors = SwanTheme.colors
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = colors.onTopBar, fontSize = 14.sp)
    }
}

@Composable
private fun FolderPickerSheet(
    folders: List<NoteFolder>,
    currentFolderId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SwanBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "移动到文件夹",
            color = SwanTheme.colors.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        ListSheetRow(
            label = "全部便签",
            selected = currentFolderId == null,
            onClick = { onSelect(null) },
        )
        for (folder in folders) {
            ListSheetRow(
                label = folder.name,
                selected = currentFolderId == folder.id,
                onClick = { onSelect(folder.id) },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ShareSheet(
    markdown: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    SwanBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "分享便签",
            color = SwanTheme.colors.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        DialogButton(
            text = "复制文字",
            primary = true,
            onClick = onCopy,
            enabled = markdown.isNotEmpty(),
        )
        Spacer(Modifier.height(10.dp))
        DialogButton(
            text = "系统分享",
            primary = false,
            onClick = onShare,
            enabled = markdown.isNotEmpty(),
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ImageLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, alt: String) -> Unit,
) {
    val colors = SwanTheme.colors
    var url by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.dialogBackground,
            modifier = Modifier.width(320.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "插入图片链接", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Text(text = "图片地址", color = colors.mutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SwanTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "https://...",
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "说明文字（可选）", color = colors.mutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SwanTextField(
                    value = alt,
                    onValueChange = { alt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "图片说明",
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DialogButton(text = "取消", primary = false, onClick = onDismiss)
                    DialogButton(
                        text = "插入",
                        primary = true,
                        onClick = { onConfirm(url, alt) },
                    )
                }
            }
        }
    }
}





