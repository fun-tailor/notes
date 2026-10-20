package com.swan.notes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = WarmColors.primary,
    onPrimary = Color.White,
    primaryContainer = WarmColors.primaryDark,
    onPrimaryContainer = Color.White,
    background = WarmColors.screenBackground,
    onBackground = WarmColors.ink,
    surface = WarmColors.editorPaper,
    onSurface = WarmColors.editorText,
    surfaceVariant = WarmColors.listSurface,
    onSurfaceVariant = WarmColors.mutedInk,
    outline = WarmColors.divider,
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = DarkColors.primary,
    onPrimary = Color(0xFF2A1B0D),
    primaryContainer = DarkColors.primaryDark,
    onPrimaryContainer = Color.White,
    background = DarkColors.screenBackground,
    onBackground = DarkColors.ink,
    surface = DarkColors.editorPaper,
    onSurface = DarkColors.editorText,
    surfaceVariant = DarkColors.listSurface,
    onSurfaceVariant = DarkColors.mutedInk,
    outline = DarkColors.divider,
    error = Color(0xFFF2B8B5),
)

@Composable
fun SwanTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else WarmColors
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !colors.dark
    }

    CompositionLocalProvider(LocalSwanColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography(),
            content = content,
        )
    }
}

object SwanTheme {
    val colors: SwanColors
        @Composable get() = LocalSwanColors.current
}
