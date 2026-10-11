package com.example.task_automator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F1), onPrimary = Color(0xFF202020),
    primaryContainer = Color(0xFF353535), onPrimaryContainer = Color(0xFFF1F1F1),
    secondary = Color(0xFFBBBBBB), onSecondary = Color(0xFF202020),
    background = Color(0xFF212121), onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF212121), onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF555555), outlineVariant = Color(0xFF393939),
    error = Color(0xFFFFB4AB)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF202020), onPrimary = Color.White,
    primaryContainer = Color(0xFFECECEC), onPrimaryContainer = Color(0xFF202020),
    secondary = Color(0xFF666666), onSecondary = Color.White,
    background = Color.White, onBackground = Color(0xFF202020),
    surface = Color.White, onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFF3F3F3), onSurfaceVariant = Color(0xFF707070),
    outline = Color(0xFFCCCCCC), outlineVariant = Color(0xFFECECEC),
    error = Color(0xFFB3261E)
)
@Composable
fun TaskAutomatorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
