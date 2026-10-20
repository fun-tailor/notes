package com.swan.notes.ui.category

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swan.notes.R
import com.swan.notes.data.NoteCategory
import com.swan.notes.data.NoteDocument
import com.swan.notes.data.NoteFolder
import com.swan.notes.data.isNoteDeleted
import com.swan.notes.ui.theme.SwanTheme
import java.text.Collator
import java.util.Locale

@Composable
fun CategoryOverlay(
    folders: List<NoteFolder>,
    notes: List<NoteDocument>,
    category: NoteCategory,
    sortDirection: String,
    onSelect: (NoteCategory) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onToggleSortDirection: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SwanTheme.colors
    val collator = remember { Collator.getInstance(Locale.SIMPLIFIED_CHINESE) }
    val sortedFolders = remember(folders, sortDirection) {
        val sorted = folders.sortedWith { left, right -> collator.compare(left.name, right.name) }
        if (sortDirection == "desc") sorted.reversed() else sorted
    }

    val nonDeleted = notes.count { !isNoteDeleted(it) }
    val starred = notes.count { !isNoteDeleted(it) && it.isStarred }
    val trash = notes.count { isNoteDeleted(it) }

    var editing by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var pendingDeleteFolder by remember { mutableStateOf<NoteFolder?>(null) }

    val cardBackground = if (colors.dark) Color(0xFF201E20) else Color.White
    val borderColor = if (colors.dark) Color(0xFF343134) else Color(0xFFCFCAC5)
    val rowDivider = if (colors.dark) Color(0xFF282828) else Color(0xFFE6E4E2)
    val selectedRow = if (colors.dark) Color(0xFF2A272A) else Color(0xFFFAF9F8)
    val rowText = if (colors.dark) Color(0xFFCECECE) else Color(0xFF686868)
    val countText = if (colors.dark) Color(0xFF777477) else Color(0xFFB7B4B1)
    val footerBackground = if (colors.dark) Color(0xFF1A181A) else Color(0xFFF7F7F7)
    val footerBorder = if (colors.dark) Color(0xFF343134) else Color(0xFFE0DEDC)
    val footerText = if (colors.dark) Color(0xFFCECECE) else Color(0xFF686868)
    val cardShape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val cardMaxHeight = (maxHeight - 80.dp).coerceAtLeast(120.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, start = 31.dp, end = 31.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cardMaxHeight)
                        .shadow(14.dp, cardShape)
                        .clip(cardShape)
                        .background(cardBackground)
                        .border(1.dp, borderColor, cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        CategoryRow(
                            iconRes = R.drawable.ic_category_all,
                            label = "全部便签",
                            count = nonDeleted,
                            selected = category == NoteCategory.All,
                            editing = false,
                            showDelete = false,
                            onClick = { onSelect(NoteCategory.All) },
                            onDelete = {},
                            rowText = rowText,
                            countText = countText,
                            selectedRow = selectedRow,
                            divider = rowDivider,
                        )
                        CategoryRow(
                            iconRes = R.drawable.ic_category_starred,
                            label = "加星便签",
                            count = starred,
                            selected = category == NoteCategory.Starred,
                            editing = false,
                            showDelete = false,
                            onClick = { onSelect(NoteCategory.Starred) },
                            onDelete = {},
                            rowText = rowText,
                            countText = countText,
                            selectedRow = selectedRow,
                            divider = rowDivider,
                        )

                        for (folder in sortedFolders) {
                            CategoryRow(
                                iconRes = R.drawable.ic_category_folder,
                                label = folder.name,
                                count = notes.count { !isNoteDeleted(it) && it.folderId == folder.id },
                                selected = (category as? NoteCategory.Folder)?.folderId == folder.id,
                                editing = editing,
                                showDelete = true,
                                onClick = { onSelect(NoteCategory.Folder(folder.id)) },
                                onDelete = { pendingDeleteFolder = folder },
                                rowText = rowText,
                                countText = countText,
                                selectedRow = selectedRow,
                                divider = rowDivider,
                            )
                        }

                        CategoryRow(
                            iconRes = R.drawable.ic_category_trash,
                            label = "回收站",
                            count = trash,
                            selected = category == NoteCategory.Trash,
                            editing = false,
                            showDelete = false,
                            onClick = { onSelect(NoteCategory.Trash) },
                            onDelete = {},
                            rowText = rowText,
                            countText = countText,
                            selectedRow = selectedRow,
                            divider = Color.Transparent,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(footerBorder),
                    )

                    if (creatingFolder) {
                        NewFolderForm(
                            name = newFolderName,
                            onNameChange = { newFolderName = it },
                            onConfirm = {
                                val trimmed = newFolderName.trim()
                                if (trimmed.isNotEmpty()) {
                                    val existing = folders.firstOrNull { it.name.trim() == trimmed }
                                    if (existing != null) {
                                        onSelect(NoteCategory.Folder(existing.id))
                                    } else {
                                        onCreateFolder(trimmed.take(24))
                                    }
                                }
                                newFolderName = ""
                                creatingFolder = false
                            },
                            onCancel = {
                                newFolderName = ""
                                creatingFolder = false
                            },
                            background = selectedRow,
                            divider = footerBorder,
                            textColor = rowText,
                            hintColor = countText,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .background(footerBackground)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FooterButton(
                            label = if (editing) "完成" else "编辑",
                            onClick = { editing = !editing },
                            color = footerText,
                        )
                        Spacer(Modifier.weight(1f))
                        FooterButton(
                            label = if (sortDirection == "asc") "A↓" else "A↑",
                            onClick = onToggleSortDirection,
                            color = footerText,
                        )
                        FooterButton(
                            label = "新建文件夹",
                            onClick = {
                                creatingFolder = true
                                editing = false
                            },
                            color = footerText,
                        )
                    }
                }
            }
        }
    }

    pendingDeleteFolder?.let { folder ->
        com.swan.notes.ui.common.SwanConfirmDialog(
            title = "删除文件夹",
            message = "删除文件夹？其中的便签会保留在「全部便签」",
            confirmLabel = "删除",
            onConfirm = {
                pendingDeleteFolder = null
                onDeleteFolder(folder.id)
            },
            onDismiss = { pendingDeleteFolder = null },
        )
    }
}

@Composable
private fun CategoryRow(
    iconRes: Int,
    label: String,
    count: Int,
    selected: Boolean,
    editing: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    rowText: Color,
    countText: Color,
    selectedRow: Color,
    divider: Color,
) {
    val dark = SwanTheme.colors.dark
    Column(modifier = Modifier.fillMaxWidth().background(if (selected) selectedRow else Color.Transparent)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (dark) ColorFilter.tint(Color(0xFF9A979A)) else null,
                    modifier = Modifier.size(21.6.dp),
                )
            }
            Text(
                text = label,
                color = rowText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            if (editing && showDelete) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onDelete,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "×", color = countText, fontSize = 20.sp)
                }
            } else {
                Text(text = count.toString(), color = countText, fontSize = 14.sp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(start = 17.dp, end = 17.dp)
                .background(divider),
        )
    }
}

@Composable
private fun FooterButton(
    label: String,
    onClick: () -> Unit,
    color: Color,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
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
        Text(text = label, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun NewFolderForm(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    background: Color,
    divider: Color,
    textColor: Color,
    hintColor: Color,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier = Modifier.fillMaxWidth().background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (SwanTheme.colors.dark) Color(0xFF131113) else Color.White)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) {
                    Text(text = "文件夹名称", color = hintColor, fontSize = 13.sp, maxLines = 1)
                }
                BasicTextField(
                    value = name,
                    onValueChange = { next -> onNameChange(next.take(24)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = textColor, fontSize = 14.sp),
                    cursorBrush = SolidColor(SwanTheme.colors.caret),
                )
            }
            FooterButton(label = "确定", onClick = onConfirm, color = textColor)
            FooterButton(label = "取消", onClick = onCancel, color = hintColor)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )
    }
}
