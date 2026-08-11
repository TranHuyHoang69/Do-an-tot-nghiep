package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.TransactionType

data class CategoryModel(
    val id: String,
    val name: String,
    val type: TransactionType,
    val iconHex: String,
    val colorHex: String,
    val isDefault: Boolean,
    val isArchived: Boolean
)