package com.example.moneymatev2.ui.item

import android.app.Application
import android.content.res.Configuration
import com.example.moneymatev2.data.local.CategoryStableId
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryDisplayNameTest {
    @Test fun allEightDefaultNamesHaveVietnameseAndEnglishLabels() {
        val expected = listOf(
            Triple(CategoryStableId.SPEND_FOOD, "Ăn uống", "Food & Drinks"),
            Triple(CategoryStableId.SPEND_SHOPPING, "Mua sắm", "Shopping"),
            Triple(CategoryStableId.SPEND_HEALTH, "Sức khỏe", "Health"),
            Triple(CategoryStableId.SPEND_TRANSPORT, "Đi lại", "Transport"),
            Triple(CategoryStableId.SPEND_GIFT, "Quà tặng", "Gifts"),
            Triple(CategoryStableId.SPEND_ENTERTAINMENT, "Giải trí", "Entertainment"),
            Triple(CategoryStableId.SPEND_OTHER, "Khác", "Other"),
            Triple(CategoryStableId.INCOME_SALARY, "Tiền lương", "Salary")
        )
        expected.forEach { (id, vietnamese, english) ->
            val category = category(id)
            val resId = checkNotNull(defaultCategoryNameResId(category))
            assertEquals(id, vietnamese, label(resId, "vi"))
            assertEquals(id, english, label(resId, "en"))
        }
    }

    @Test fun oldTransportLabelChangesButCustomNamesStayUntouched() {
        val transport = category(CategoryStableId.SPEND_TRANSPORT).copy(name = "Di chuyển")
        assertEquals("Đi lại", label(checkNotNull(defaultCategoryNameResId(transport)), "vi"))
        assertNull(defaultCategoryNameResId(transport.copy(name = "Xe của tôi")))
        assertNull(defaultCategoryNameResId(category(CategoryStableId.SPEND_HEALTH)
            .copy(name = "Sức khỏe", isDefault = false)))
        assertNull(defaultCategoryNameResId(category(CategoryStableId.SPEND_FOOD)
            .copy(name = "Ăn uống gia đình")))
    }

    private fun label(resId: Int, locale: String): String {
        val context = RuntimeEnvironment.getApplication()
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(locale))
        }
        return context.createConfigurationContext(config).getString(resId)
    }

    private fun category(id: String) = CategoryModel("owner", id, id, id,
        if (id == CategoryStableId.INCOME_SALARY) TransactionType.INCOME else TransactionType.EXPENSE,
        "ic_food", "#9E9E9E", true, false)
}
