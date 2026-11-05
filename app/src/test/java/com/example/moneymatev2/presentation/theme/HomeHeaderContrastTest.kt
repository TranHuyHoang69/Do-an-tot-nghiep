package com.example.moneymatev2.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderContrastTest {
    @Test
    fun homeTabsRemainReadableOnGreenHeaderInBothThemes() {
        val unselectedBackground = AppTopBarTabContainerColor.compositeOver(AppTopBarColor)
        val selectedBackground = AppTopBarTabSelectedColor.compositeOver(unselectedBackground)

        assertTrue(contrast(AppTopBarContentColor, unselectedBackground) >= 4.5)
        assertTrue(contrast(AppTopBarContentColor, selectedBackground) >= 4.5)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
        val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }
}
