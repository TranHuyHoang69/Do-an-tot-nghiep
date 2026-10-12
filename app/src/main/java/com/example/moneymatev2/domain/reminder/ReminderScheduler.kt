package com.example.moneymatev2.domain.reminder

import com.example.moneymatev2.domain.model.ReminderModel

interface ReminderScheduler {

    fun schedule(reminder: ReminderModel)

    fun cancel(reminderId: String)

    fun canScheduleExactAlarms(): Boolean
}