package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.TransactionType

data class CategoryModel(
    val userId: String,
    val id: String,
    val stableId: String?,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorHex: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val isMissing: Boolean = false
)

/** Display-only placeholder; never persisted, selected or used to repair references. */
fun missingCategory(userId: String, id: String, type: TransactionType) = CategoryModel(
    userId = userId, id = id, stableId = null, name = "Missing category",
    type = type, iconKey = "ic_category", colorHex = "#9E9E9E",
    isDefault = false, isArchived = true, isMissing = true
)
