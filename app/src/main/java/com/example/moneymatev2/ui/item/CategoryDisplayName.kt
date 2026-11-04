package com.example.moneymatev2.ui.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.CategoryStableId
import com.example.moneymatev2.domain.model.CategoryModel

/** Translate only untouched default names. Custom category names stay exactly as stored. */
@Composable
fun categoryDisplayName(category: CategoryModel): String {
    val nameId = defaultCategoryNameResId(category) ?: return category.name
    return stringResource(nameId)
}

internal fun defaultCategoryNameResId(category: CategoryModel): Int? {
    if (!category.isDefault || !usesDefaultName(category)) return null
    return when (category.stableId) {
        CategoryStableId.SPEND_FOOD -> R.string.statistics_default_food
        CategoryStableId.SPEND_SHOPPING -> R.string.statistics_default_shopping
        CategoryStableId.SPEND_HEALTH -> R.string.statistics_default_health
        CategoryStableId.SPEND_TRANSPORT -> R.string.statistics_default_transport
        CategoryStableId.SPEND_GIFT -> R.string.statistics_default_gift
        CategoryStableId.SPEND_ENTERTAINMENT -> R.string.statistics_default_entertainment
        CategoryStableId.SPEND_OTHER -> R.string.statistics_default_other
        CategoryStableId.INCOME_SALARY -> R.string.statistics_default_salary
        else -> null
    }
}

internal fun usesDefaultName(category: CategoryModel): Boolean {
    val stableId = category.stableId ?: return false
    if (category.name == stableId) return true
    // Earlier builds stored these translated labels directly in some default rows.
    return category.name in when (stableId) {
        CategoryStableId.SPEND_FOOD -> setOf("Ăn uống", "Food")
        CategoryStableId.SPEND_SHOPPING -> setOf("Mua sắm", "Shopping")
        CategoryStableId.SPEND_TRANSPORT -> setOf("Di chuyển", "Transport")
        CategoryStableId.INCOME_SALARY -> setOf("Lương", "Salary")
        else -> emptySet()
    }
}
