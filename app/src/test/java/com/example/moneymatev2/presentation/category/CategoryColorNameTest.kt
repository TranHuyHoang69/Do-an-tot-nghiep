package com.example.moneymatev2.presentation.category

import android.app.Application
import android.content.res.Configuration
import com.example.moneymatev2.R
import com.example.moneymatev2.core.util.CategoryColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryColorNameTest {
    @Test fun everyPaletteColorHasItsOwnLocalizedAccessibleName() {
        val colors = CategoryColorPalette.colors
        val nameIds = colors.map(::categoryColorNameRes)
        assertEquals(16, colors.size)
        assertEquals(colors.size, nameIds.distinct().size)
        nameIds.forEach { id ->
            assertNotEquals(R.string.category_color, id)
            assertTrue(localized("vi", id).isNotBlank())
            assertTrue(localized("en", id).isNotBlank())
        }
        assertEquals(R.string.category_color_red, categoryColorNameRes("#F44336"))
        assertEquals(R.string.category_color_blue, categoryColorNameRes("#2196F3"))
    }

    @Test fun changedHistoryAndCategoryMessagesHaveBothLanguages() {
        val ids = listOf(R.string.category_save_failed, R.string.history_search_hint,
            R.string.history_search_guide, R.string.history_previous_period,
            R.string.history_next_period)
        ids.forEach { id ->
            assertTrue(localized("vi", id).isNotBlank())
            assertTrue(localized("en", id).isNotBlank())
            assertNotEquals(localized("vi", id), localized("en", id))
        }
        assertTrue(localized("vi", R.string.history_search_guide).contains("50000-200000"))
        assertTrue(localized("en", R.string.history_search_guide).contains("50000-200000"))
    }

    private fun localized(language: String, id: Int): String {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(config).getString(id)
    }
}
