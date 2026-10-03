package io.github.goumang.txtnote.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.goumang.txtnote.model.Preferences
import io.github.goumang.txtnote.model.ThemeColor
import io.github.goumang.txtnote.model.isValidThemeHex

data class ThemePalette(val ink: Color, val accent: Color)

fun ThemeColor.palette(): ThemePalette = when (this) {
    ThemeColor.GREEN -> ThemePalette(Color(0xFF183E36), Color(0xFFB5EBD0))
    ThemeColor.BLUE -> ThemePalette(Color(0xFF173D70), Color(0xFFB8D4FF))
    ThemeColor.PURPLE -> ThemePalette(Color(0xFF4A2C70), Color(0xFFD8C3FF))
    ThemeColor.ORANGE -> ThemePalette(Color(0xFF6B3B1F), Color(0xFFFAD2B3))
    ThemeColor.ROSE -> ThemePalette(Color(0xFF6B3045), Color(0xFFF5C2D5))
    ThemeColor.GRAPHITE -> ThemePalette(Color(0xFF303B4B), Color(0xFFCED5E0))
}

fun Preferences.palette(): ThemePalette {
    val hex = customThemeColor?.takeIf(::isValidThemeHex) ?: return themeColor.palette()
    val seed = Color(0xFF000000L or hex.drop(1).toLong(16))
    // Derive a dark base and pale accent so any custom hue keeps readable text in both modes.
    return ThemePalette(lerp(seed, Color.Black, .64f), lerp(seed, Color.White, .72f))
}

val LocalThemePalette = staticCompositionLocalOf { ThemeColor.GREEN.palette() }

fun ThemePalette.colorScheme(dark: Boolean): ColorScheme = if (dark) {
    val container = lerp(ink, Color.White, .10f)
    darkColorScheme(primary = accent, onPrimary = ink, primaryContainer = container, onPrimaryContainer = accent,
        secondary = accent, onSecondary = ink, secondaryContainer = container, onSecondaryContainer = accent,
        surface = lerp(ink, Color.Black, .5f), background = lerp(ink, Color.Black, .7f))
} else {
    val container = lerp(Color.White, accent, .32f)
    lightColorScheme(primary = ink, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = ink,
        secondary = ink, onSecondary = Color.White, secondaryContainer = container, onSecondaryContainer = ink,
        surface = Color.White, background = lerp(Color.White, ink, .025f), surfaceVariant = lerp(Color.White, accent, .26f))
}
