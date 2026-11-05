package com.example.moneymatev2.data.local

import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.domain.model.CategoryModel

/** Identity rule shared by the management UI and every repository archive path. */
object ProtectedDefaultCategory {
    val stableIds = setOf(
        CategoryStableId.SPEND_FOOD, CategoryStableId.SPEND_SHOPPING,
        CategoryStableId.SPEND_HEALTH, CategoryStableId.SPEND_TRANSPORT,
        CategoryStableId.SPEND_GIFT, CategoryStableId.SPEND_ENTERTAINMENT,
        CategoryStableId.SPEND_OTHER, CategoryStableId.INCOME_SALARY
    )

    fun matches(row: CategoryEntity): Boolean = matches(row.stableId, row.isDefault)

    fun matches(row: CategoryModel): Boolean = matches(row.stableId, row.isDefault)

    private fun matches(stableId: String?, isDefault: Boolean): Boolean {
        if (stableId != null) return stableId in stableIds
        // Legacy defaults can lack a stable ID. Their protection must survive
        // name/icon/color edits without changing identity or relying on a name.
        return isDefault
    }
}
