package com.example.moneymatev2.data.local

import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.TransactionType
import java.util.Locale
import java.util.UUID

object CategoryStableId {
    const val SPEND_FOOD = "spend_food"
    const val SPEND_SHOPPING = "spend_shopping"
    const val SPEND_TRANSPORT = "spend_transport"
    const val INCOME_SALARY = "income_salary"
    const val SPEND_HEALTH = "spend_health"
    const val SPEND_ENTERTAINMENT = "spend_entertainment"
    const val SPEND_COFFEE = "spend_coffee"
    const val SPEND_GIFT = "spend_gift"


    fun ensureStableId(category: CategoryEntity): CategoryEntity {
        if (!category.stableId.isNullOrBlank()) return category

        val stableId = if (category.isDefault) {
            defaultStableId(category.type, category.iconKey)
                ?: defaultFallbackStableId(category.type, category.iconKey)
        } else {
            newCustomStableId()
        }
        return category.copy(stableId = stableId)
    }

    fun defaultStableId(type: TransactionType, iconKey: String): String? {
        return when (type to iconKey) {
            TransactionType.EXPENSE to "ic_food" -> SPEND_FOOD
            TransactionType.EXPENSE to "ic_shop" -> SPEND_SHOPPING
            TransactionType.EXPENSE to "ic_car" -> SPEND_TRANSPORT
            TransactionType.INCOME to "ic_money" -> INCOME_SALARY
            TransactionType.EXPENSE to "ic_cat_health_health" -> SPEND_HEALTH
            TransactionType.EXPENSE to "ic_cat_finance_wallet" -> SPEND_ENTERTAINMENT
            TransactionType.EXPENSE to "ic_cat_food_coffee" -> SPEND_COFFEE
            TransactionType.EXPENSE to "ic_cat_shop_gift" -> SPEND_GIFT
            else -> null
        }
    }

    fun newCustomStableId(): String = "custom_${UUID.randomUUID()}"
    fun legacyStableId(categoryId: Long): String = "legacy_$categoryId"

    private fun defaultFallbackStableId(type: TransactionType, iconKey: String): String {
        val normalizedType = type.name.lowercase(Locale.US)
        val normalizedIcon = iconKey
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "default" }
        return "default_${normalizedType}_${normalizedIcon}"
    }
}