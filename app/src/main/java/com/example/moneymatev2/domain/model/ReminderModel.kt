package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.RepeatRule

data class ReminderModel(
    val id: String,
    val title: String,
    val description: String?,
    val amount: Money,
    val dueDate: Long,
    val repeatRule: RepeatRule,
    val isCompleted: Boolean
)