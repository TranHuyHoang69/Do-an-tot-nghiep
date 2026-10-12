package com.example.moneymatev2.domain.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import java.util.Calendar
import javax.inject.Inject

class ReminderRepeatCalculator @Inject constructor() {

    fun calculateNextTrigger(
        currentTriggerAt: Long,
        repeatRule: RepeatRule,
        now: Long = System.currentTimeMillis()
    ): Long? {

        return when (repeatRule) {

            RepeatRule.NONE -> null

            RepeatRule.DAILY ->
                calculateDaily(
                    currentTriggerAt = currentTriggerAt,
                    now = now
                )

            RepeatRule.WEEKLY ->
                calculateWeekly(
                    currentTriggerAt = currentTriggerAt,
                    now = now
                )

            RepeatRule.MONTHLY ->
                calculateMonthly(
                    currentTriggerAt = currentTriggerAt,
                    now = now
                )

            else -> null
        }
    }

    private fun calculateDaily(
        currentTriggerAt: Long,
        now: Long
    ): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = currentTriggerAt
            }

        do {
            calendar.add(
                Calendar.DAY_OF_YEAR,
                1
            )
        } while (calendar.timeInMillis <= now)

        return calendar.timeInMillis
    }

    private fun calculateWeekly(
        currentTriggerAt: Long,
        now: Long
    ): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = currentTriggerAt
            }

        do {
            calendar.add(
                Calendar.WEEK_OF_YEAR,
                1
            )
        } while (calendar.timeInMillis <= now)

        return calendar.timeInMillis
    }

    private fun calculateMonthly(
        currentTriggerAt: Long,
        now: Long
    ): Long {
        val originalCalendar =
            Calendar.getInstance().apply {
                timeInMillis = currentTriggerAt
            }

        val originalDay =
            originalCalendar.get(
                Calendar.DAY_OF_MONTH
            )

        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = currentTriggerAt
            }

        do {
            calendar.set(
                Calendar.DAY_OF_MONTH,
                1
            )

            calendar.add(
                Calendar.MONTH,
                1
            )

            val maxDay =
                calendar.getActualMaximum(
                    Calendar.DAY_OF_MONTH
                )

            calendar.set(
                Calendar.DAY_OF_MONTH,
                minOf(
                    originalDay,
                    maxDay
                )
            )

        } while (calendar.timeInMillis <= now)

        return calendar.timeInMillis
    }
}