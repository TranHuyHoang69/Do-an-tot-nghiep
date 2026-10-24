package com.example.moneymatev2.domain.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import java.util.Calendar
import javax.inject.Inject

class ReminderRepeatCalculator @Inject constructor() {

    fun calculateNextTrigger(
        currentTriggerAt: Long,
        repeatRule: RepeatRule,
        now: Long = System.currentTimeMillis(),
        anchorAt: Long? = null
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
                    now = now,
                    anchorAt = anchorAt ?: currentTriggerAt
                )

            RepeatRule.YEARLY ->
                calculateYearly(
                    currentTriggerAt = currentTriggerAt,
                    now = now,
                    anchorAt = anchorAt ?: currentTriggerAt
                )
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
        now: Long,
        anchorAt: Long
    ): Long {
        val anchor =
            Calendar.getInstance().apply {
                timeInMillis = anchorAt
            }
        val current =
            Calendar.getInstance().apply {
                timeInMillis = currentTriggerAt
            }
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = anchorAt
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, current.get(Calendar.YEAR))
                set(Calendar.MONTH, current.get(Calendar.MONTH))
            }
        do {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MONTH, 1)
            calendar.set(
                Calendar.DAY_OF_MONTH,
                minOf(anchor.get(Calendar.DAY_OF_MONTH), calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
        } while (calendar.timeInMillis <= now || calendar.timeInMillis <= currentTriggerAt)

        return calendar.timeInMillis
    }

    private fun calculateYearly(
        currentTriggerAt: Long,
        now: Long,
        anchorAt: Long
    ): Long {
        val anchor = Calendar.getInstance().apply { timeInMillis = anchorAt }
        val current = Calendar.getInstance().apply { timeInMillis = currentTriggerAt }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = anchorAt
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, current.get(Calendar.YEAR))
        }
        do {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.YEAR, 1)
            calendar.set(
                Calendar.DAY_OF_MONTH,
                minOf(anchor.get(Calendar.DAY_OF_MONTH), calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
        } while (calendar.timeInMillis <= now || calendar.timeInMillis <= currentTriggerAt)

        return calendar.timeInMillis
    }
}
