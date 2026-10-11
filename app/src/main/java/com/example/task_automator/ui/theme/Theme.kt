package com.example.task_automator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Navy foundations, teal actions and muted blue surfaces echo the Solvix logo.
// Explicit container colors also keep menus, sheets and dialogs in the same palette.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF81D8D0), onPrimary = Color(0xFF073A3C),
    primaryContainer = Color(0xFF163E43), onPrimaryContainer = Color(0xFFBDEDE7),
    inversePrimary = Color(0xFF007780),
    secondary = Color(0xFFA9C3DF), onSecondary = Color(0xFF16304A),
    secondaryContainer = Color(0xFF24384E), onSecondaryContainer = Color(0xFFDDEAF8),
    tertiary = Color(0xFFB7C6E8), onTertiary = Color(0xFF263550),
    tertiaryContainer = Color(0xFF2D3F5D), onTertiaryContainer = Color(0xFFE0E9FC),
    background = Color(0xFF101A28), onBackground = Color(0xFFE6EEF5),
    surface = Color(0xFF101A28), onSurface = Color(0xFFE6EEF5),
    surfaceVariant = Color(0xFF1E2D3C), onSurfaceVariant = Color(0xFFADBDCD),
    surfaceTint = Color(0xFF81D8D0),
    surfaceDim = Color(0xFF101A28), surfaceBright = Color(0xFF344457),
    surfaceContainerLowest = Color(0xFF0B1420), surfaceContainerLow = Color(0xFF162231),
    surfaceContainer = Color(0xFF1B2939), surfaceContainerHigh = Color(0xFF233244),
    surfaceContainerHighest = Color(0xFF2D3D50),
    inverseSurface = Color(0xFFE6EEF5), inverseOnSurface = Color(0xFF15273E),
    outline = Color(0xFF73889C), outlineVariant = Color(0xFF304355),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF007780), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3F1), onPrimaryContainer = Color(0xFF12464B),
    inversePrimary = Color(0xFF81D8D0),
    secondary = Color(0xFF3B5974), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EEF7), onSecondaryContainer = Color(0xFF213B56),
    tertiary = Color(0xFF526B98), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5ECFA), onTertiaryContainer = Color(0xFF263D63),
    background = Color(0xFFFAFCFD), onBackground = Color(0xFF15273E),
    surface = Color.White, onSurface = Color(0xFF15273E),
    surfaceVariant = Color(0xFFEDF3F6), onSurfaceVariant = Color(0xFF526277),
    surfaceTint = Color(0xFF007780),
    surfaceDim = Color(0xFFDCE5EB), surfaceBright = Color(0xFFFAFCFD),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F7FA),
    surfaceContainer = Color(0xFFEDF3F6), surfaceContainerHigh = Color(0xFFE7EEF3),
    surfaceContainerHighest = Color(0xFFE0E9EF),
    inverseSurface = Color(0xFF24384E), inverseOnSurface = Color(0xFFE6EEF5),
    outline = Color(0xFF94A5B4), outlineVariant = Color(0xFFD6E0E7),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
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
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography) {
        // Plain chat text sits directly in a layout, outside a Material Surface.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
