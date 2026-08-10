package com.example.moneymatev2.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithCategory(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "categoryLocalId",
        entityColumn = "localId"
    )
    val category: CategoryEntity?
)
