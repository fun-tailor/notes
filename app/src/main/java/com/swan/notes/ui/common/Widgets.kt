package com.swan.notes.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swan.notes.R
import com.swan.notes.ui.theme.SwanColors
import com.swan.notes.ui.theme.SwanTheme
import java.util.Calendar

@Composable
fun TopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = SwanTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.topBar),
    ) {
        Image(
            painter = painterResource(
                if (colors.dark) R.drawable.action_bar_default_dark else R.drawable.action_bar_default_no_round,
            ),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (colors.dark) 48.6667.dp else 48.dp)
                .align(Alignment.BottomCenter),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(47.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun PngIconButton(
    resourceId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    touchSize: Dp = 48.dp,
    enabled: Boolean = true,
    flipVertical: Boolean = false,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .then(if (flipVertical) Modifier.graphicsLayer { scaleY = -1f } else Modifier)
                .alpha(if (enabled) 1f else 0.45f),
            contentScale = ContentScale.Fit,
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
    }
}

@Composable
fun PaperSlice(
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = SwanTheme.colors.dark
    val suffix = if (dark) "_dark" else ""
    val state = if (pressed) "pressed" else "normal"
    Row(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Image(
            painter = painterResource(resourceId("list-item-$state-left$suffix")),
            contentDescription = null,
            modifier = Modifier
                .width(37.6667.dp)
                .fillMaxHeight(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(resourceId("list-item-$state-center$suffix")),
            contentDescription = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(resourceId("list-item-$state-right$suffix")),
            contentDescription = null,
            modifier = Modifier
                .width(37.3333.dp)
                .fillMaxHeight(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
fun ClipImage(modifier: Modifier = Modifier) {
    val dark = SwanTheme.colors.dark
    Image(
        painter = painterResource(if (dark) R.drawable.note_item_clip_normal_dark else R.drawable.note_item_clip_normal),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
fun SearchMagnifier(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val dark = SwanTheme.colors.dark
    Image(
        painter = painterResource(if (dark) R.drawable.search_bar_left_icon_dark else R.drawable.search_bar_left_icon),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun SwanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle(fontSize = 15.sp),
    maxLength: Int? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    val colors = SwanTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.searchBackground)
            .padding(horizontal = 10.dp, vertical = if (singleLine) 8.dp else 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = colors.searchHint,
                style = textStyle,
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val limited = if (maxLength != null && next.length > maxLength) next.take(maxLength) else next
                onValueChange(limited)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle.copy(color = colors.searchText),
            singleLine = singleLine,
            cursorBrush = SolidColor(colors.caret),
            keyboardOptions = keyboardOptions,
        )
    }
}

@Composable
fun SwanConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "取消",
    destructive: Boolean = true,
) {
    val colors = SwanTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.dialogBackground,
            modifier = Modifier.width(300.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    color = colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    color = colors.mutedInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DialogButton(text = cancelLabel, primary = false, onClick = onDismiss)
                    DialogButton(
                        text = confirmLabel,
                        primary = true,
                        destructive = destructive,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = SwanTheme.colors
    val background = when {
        primary && destructive -> colors.primaryDark
        primary -> colors.primary
        else -> colors.searchBackground
    }
    val contentColor = if (primary) Color.White else colors.ink
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun SwanBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwanTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(colors.dialogBackground)
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.divider),
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun ListSheetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.searchBackground else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.ink,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                color = colors.accentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val colors = SwanTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = colors.metaText, fontSize = 14.sp)
    }
}

@Composable
fun DimBackground(dark: Boolean, modifier: Modifier = Modifier) {
    val colors = SwanTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.screenBackground)) {
        Image(
            painter = painterResource(if (dark) R.drawable.note_background_dark else R.drawable.note_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
fun SegmentTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwanTheme.colors
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.settingsSelectedTab else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.settingsTitle else colors.settingsValue,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwanTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.settingsCard)
            .padding(vertical = 2.dp),
        content = content,
    )
}

@Composable
fun SettingsGroupTitle(title: String, description: String) {
    val colors = SwanTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(text = title, color = colors.settingsGroupTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (description.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(text = description, color = colors.settingsSubtitle, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = SwanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) colors.settingsTitle else colors.settingsTitle.copy(alpha = 0.4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, color = colors.settingsSubtitle, fontSize = 12.sp)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = value,
                color = if (enabled) colors.settingsValue else colors.settingsValue.copy(alpha = 0.4f),
                fontSize = 14.sp,
            )
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                Text(text = "›", color = colors.settingsValue, fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun SettingsRowDivider(inset: Dp = 18.dp) {
    val colors = SwanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset)
            .height(1.dp)
            .background(colors.settingsDivider),
    )
}

fun formatNoteDate(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0) return ""
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }

    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun time(): String {
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$hour:$minute"
    }

    return when {
        sameDay(calendar, today) -> "今天 ${time()}"
        sameDay(calendar, yesterday) -> "昨天 ${time()}"
        else -> "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)} ${time()}"
    }
}

fun resourceId(name: String): Int {
    val field = R.drawable::class.java.getField(name.replace('-', '_'))
    return field.getInt(null)
}

@Composable
fun GradientHairline(modifier: Modifier = Modifier) {
    val colors = SwanTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(colors.divider, colors.divider.copy(alpha = 0.45f), colors.divider),
                ),
            ),
    )
}

@Composable
fun SettingsPageTitle(title: String, subtitle: String) {
    val colors = SwanTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(text = title, color = colors.settingsTitle, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, color = colors.settingsSubtitle, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
fun VerticalHairlines(colors: SwanColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.34f to Color.White.copy(alpha = 0.04f),
                    1f to Color.Transparent,
                ),
            )
            .wrapContentHeight(),
    )
}
