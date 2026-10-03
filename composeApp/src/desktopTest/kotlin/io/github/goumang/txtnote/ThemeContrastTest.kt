package io.github.goumang.txtnote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.goumang.txtnote.model.Preferences
import io.github.goumang.txtnote.model.ThemeColor
import io.github.goumang.txtnote.ui.colorScheme
import io.github.goumang.txtnote.ui.palette
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test fun primaryButtonsRemainReadableAcrossPresetsAndCustomColorExtremes() {
        val palettes = ThemeColor.entries.map { it.palette() } + listOf("#FFFFFF", "#000000", "#FF0000", "#00FF00", "#0000FF", "#FF8800").map { Preferences(customThemeColor = it).palette() }
        palettes.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val colors = palette.colorScheme(dark)
                assertTrue(contrast(colors.primary, colors.onPrimary) >= 4.5f, "Primary text should meet 4.5:1 contrast")
            }
            assertTrue(contrast(palette.ink, Color.White) >= 4.5f, "Sidebar text should remain readable")
        }
    }
    private fun contrast(a: Color, b: Color): Float = (max(a.luminance(), b.luminance()) + .05f) / (min(a.luminance(), b.luminance()) + .05f)
}
