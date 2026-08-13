package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.RepeatRule

data class ReminderModel(
    val id: String,
    val title: String,
    val message: String?,
    val triggerAt: Long,
    val repeatRule: RepeatRule,
    val isActive: Boolean
)