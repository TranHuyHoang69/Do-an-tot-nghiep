package com.example.moneymatev2.domain.model

data class CloudPullReport(
    val categories: Int,
    val transactions: Int,
    val budgets: Int,
    val reminders: Int,
    val recurringTemplates: Int
)
