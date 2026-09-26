package com.mohdshayan.mutoscope.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccentTint,
    onPrimaryContainer = LightOnBackground,
    secondary = LightOnSurfaceVariant,
    onSecondary = LightSurface,
    secondaryContainer = LightAccentTint,
    onSecondaryContainer = LightOnBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightBackground,
    surfaceTint = Color.Transparent,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = HairlineAlpha),
    inverseSurface = LightOnBackground,
    inverseOnSurface = LightSurface,
    inversePrimary = DarkAccent,
    error = LightError,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentTint,
    onPrimaryContainer = DarkOnBackground,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkSurface,
    secondaryContainer = DarkAccentTint,
    onSecondaryContainer = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkBackground,
    surfaceTint = Color.Transparent,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = HairlineAlpha),
    inverseSurface = DarkOnBackground,
    inverseOnSurface = DarkSurface,
    inversePrimary = LightAccent,
    error = DarkError,
)

/** Canvas-only colours that Material 3 does not model. */
@Immutable
data class CanvasColors(
    val ghostBefore: Color,
    val ghostAfter: Color,
    val isDark: Boolean,
)

val LocalCanvasColors = staticCompositionLocalOf {
    CanvasColors(GhostBeforeLight, GhostAfterLight, isDark = false)
}

/**
 * The app theme. Dynamic colour is deliberately off: orchid is the app's identity and must look
 * the same on every device. System bar icons follow the mode, and LocalReducedMotion is provided
 * for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val canvas = if (darkTheme) {
        CanvasColors(GhostBeforeDark, GhostAfterDark, isDark = true)
    } else {
        CanvasColors(GhostBeforeLight, GhostAfterLight, isDark = false)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalCanvasColors provides canvas,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
