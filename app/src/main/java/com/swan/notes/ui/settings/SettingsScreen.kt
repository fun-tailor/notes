package com.swan.notes.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan.notes.data.AppSettings
import com.swan.notes.ui.NotesViewModel
import com.swan.notes.ui.common.SettingsCard
import com.swan.notes.ui.common.SettingsGroupTitle
import com.swan.notes.ui.common.SettingsPageTitle
import com.swan.notes.ui.common.SettingsRow
import com.swan.notes.ui.common.SettingsRowDivider
import com.swan.notes.ui.common.SwanConfirmDialog
import com.swan.notes.ui.common.SwanTextField
import com.swan.notes.ui.theme.SwanTheme
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) {
    General("常规"),
    Sync("本地同步"),
    About("关于"),
}

@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    onClose: () -> Unit,
) {
    val colors = SwanTheme.colors
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(SettingsTab.General) }

    BackHandler(enabled = true) { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.settingsPage)
            .drawBehind {
                if (!colors.dark) {
                    val step = 3.dp.toPx()
                    val stroke = 1.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = stroke,
                        )
                        x += step
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "设置", onClose = onClose)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (colors.dark) Color(0xFF181618) else Color.White.copy(alpha = 0.76f))
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (item in SettingsTab.entries) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.swan.notes.ui.common.SegmentTab(
                            text = item.label,
                            selected = tab == item,
                            onClick = { tab = item },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 32.dp),
            ) {
                when (tab) {
                    SettingsTab.General -> GeneralSettings(settings = settings, onThemeChange = viewModel::setTheme)
                    SettingsTab.Sync -> SyncSettings(
                        settings = settings,
                        syncStatus = syncStatus,
                        viewModel = viewModel,
                    )
                    SettingsTab.About -> AboutSettings()
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onClose: () -> Unit) {
    val colors = SwanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (colors.dark) {
                    Brush.verticalGradient(listOf(colors.settingsTopBar, colors.settingsTopBar))
                } else {
                    Brush.verticalGradient(listOf(Color.White, Color(0xFFF8F8F8)))
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.settingsTitle,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "×", color = colors.settingsTitle, fontSize = 26.sp)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.settingsCardBorder))
    }
}

@Composable
private fun GeneralSettings(
    settings: AppSettings,
    onThemeChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsPageTitle("常规", "调整便签的外观和日常使用体验。")
        SettingsGroupTitle("外观", "选择适合当前环境的便签纸张风格。")

        SettingsCard {
            ThemeOptionRow(
                title = "自动适应",
                subtitle = "跟随系统日夜切换",
                selected = settings.themePreference == AppSettings.THEME_SYSTEM,
                swatch = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFF1DFC2), Color(0xFF141114)),
                                ),
                            ),
                    )
                },
                onClick = { onThemeChange(AppSettings.THEME_SYSTEM) },
            )
            SettingsRowDivider(inset = 16.dp)
            ThemeOptionRow(
                title = "暖白纸感",
                subtitle = "默认主题",
                selected = settings.themePreference == AppSettings.THEME_DEFAULT ||
                    settings.themePreference != AppSettings.THEME_SYSTEM &&
                    settings.themePreference != AppSettings.THEME_DARK,
                swatch = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1DFC2)),
                    )
                },
                onClick = { onThemeChange(AppSettings.THEME_DEFAULT) },
            )
            SettingsRowDivider(inset = 16.dp)
            ThemeOptionRow(
                title = "深夜便签",
                subtitle = "锤子暗黑",
                selected = settings.themePreference == AppSettings.THEME_DARK,
                swatch = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF262326)),
                    )
                },
                onClick = { onThemeChange(AppSettings.THEME_DARK) },
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    swatch: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = SwanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatch()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (colors.dark) colors.settingsTitle else Color(0xFF494D5D),
                fontSize = 14.7.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = colors.settingsValue, fontSize = 12.sp)
        }
        if (selected) {
            Text(text = "✓", color = colors.accentGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SyncSettings(
    settings: AppSettings,
    syncStatus: String,
    viewModel: NotesViewModel,
) {
    val colors = SwanTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(syncStatus) {
        if (syncStatus.isNotBlank()) localStatus = syncStatus
    }

    val hasDirectory = !settings.syncTreeUri.isNullOrBlank()
    val displayStatus = when {
        syncing -> "正在同步…"
        localStatus.isNotBlank() -> localStatus
        hasDirectory -> "已选择本地文件夹。"
        else -> ""
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        viewModel.setSyncDirectory(uri, name)
        localStatus = "已选择本地文件夹。"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsPageTitle("本地同步", "把便签镜像保存到设备的固定文件夹，随时可用文件管理器查看。")
        SettingsGroupTitle("本地文件夹同步", "每条便签保存为 <id>.md。")

        SettingsCard {
            SettingsRow(
                title = if (hasDirectory) "更换同步文件夹" else "选择同步文件夹",
                subtitle = if (hasDirectory) {
                    "当前：${settings.syncDirectoryName ?: "已选择的文件夹"}"
                } else {
                    "尚未选择文件夹，便签仅保存在本机应用数据中"
                },
                value = "选择",
                onClick = { folderPicker.launch(null) },
            )
            SettingsRowDivider()
            SettingsRow(
                title = "立即同步",
                subtitle = "把当前所有便签写入同步文件夹",
                value = if (syncing) "同步中…" else "同步",
                enabled = hasDirectory && !syncing,
                onClick = {
                    if (hasDirectory) {
                        scope.launch {
                            syncing = true
                            val result = viewModel.syncNow(force = true)
                            localStatus = result.detailMessage()
                            syncing = false
                        }
                    }
                },
            )
            if (hasDirectory) {
                SettingsRowDivider()
                SettingsRow(
                    title = "断开文件夹",
                    subtitle = "只清除本机的同步设置，不会删除文件夹里的文件",
                    value = "断开",
                    onClick = { confirmDisconnect = true },
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        SettingsGroupTitle("加密镜像", "开启后使用 AES-GCM 加密便签文件（文件名以 .enc 结尾）。")

        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(text = "同步口令", color = colors.settingsTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "口令只在本次运行期间保留，重启应用后需要重新输入。",
                    color = colors.settingsSubtitle,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                SwanTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        if (settings.syncEncryptionEnabled) {
                            viewModel.setSyncPassphrase(it.trim().ifEmpty { null })
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "设置口令后可开启加密",
                )
            }
            SettingsRowDivider()
            SettingsRow(
                title = if (settings.syncEncryptionEnabled) "关闭加密" else "开启加密",
                subtitle = if (settings.syncEncryptionEnabled) {
                    "加密已开启，便签会写为 .enc 文件"
                } else {
                    "需要先输入口令"
                },
                value = if (settings.syncEncryptionEnabled) "已开启" else "已关闭",
                onClick = {
                    if (settings.syncEncryptionEnabled) {
                        viewModel.setEncryptionEnabled(false, null)
                        localStatus = "已关闭加密镜像。"
                    } else {
                        val trimmed = passphrase.trim()
                        if (trimmed.isEmpty()) {
                            localStatus = "请先输入同步口令。"
                        } else {
                            viewModel.setEncryptionEnabled(true, trimmed)
                            localStatus = "已开启加密镜像。"
                        }
                    }
                },
            )
        }

        if (displayStatus.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayStatus,
                color = if (colors.dark) colors.settingsSubtitle else Color(0xFF8B8F97),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }

    if (confirmDisconnect) {
        SwanConfirmDialog(
            title = "断开文件夹",
            message = "断开后将停止自动镜像，但不会删除同步文件夹中的文件。",
            confirmLabel = "断开",
            onConfirm = {
                confirmDisconnect = false
                viewModel.disconnectSync()
                localStatus = "已断开本地文件夹。"
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun AboutSettings() {
    val colors = SwanTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsPageTitle("关于", "")
        SettingsCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
            ) {
                Text(
                    text = "原型阶段，内容稍后补充。",
                    color = colors.settingsSubtitle,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

