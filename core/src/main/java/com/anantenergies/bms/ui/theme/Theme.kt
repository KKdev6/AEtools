package com.anantenergies.bms.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

object AeShapes {
    val card = RoundedCornerShape(12.dp)
    /** Small tiles and buttons. */
    val small = RoundedCornerShape(4.dp)
    /** Status chips and pills. */
    val pill = RoundedCornerShape(percent = 50)
}

private val LocalAeColors = staticCompositionLocalOf { DarkColors }
private val LocalAeType = staticCompositionLocalOf { DefaultType }

@Composable
fun AeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceContainerHigh = colors.surface, onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong, outlineVariant = colors.border, error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceContainerHigh = colors.surface, onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong, outlineVariant = colors.border, error = colors.error,
        )
    }
    CompositionLocalProvider(LocalAeColors provides colors, LocalAeType provides DefaultType) {
        MaterialTheme(colorScheme = scheme, typography = MaterialType) {
            CompositionLocalProvider(
                LocalContentColor provides colors.textPrimary,
                LocalTextStyle provides DefaultType.body,
                content = content,
            )
        }
    }
}

object AeTheme {
    val colors: AeColors
        @Composable @ReadOnlyComposable get() = LocalAeColors.current

    val type: AeType
        @Composable @ReadOnlyComposable get() = LocalAeType.current
}
