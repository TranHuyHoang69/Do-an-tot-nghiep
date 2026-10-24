package com.example.moneymatev2.domain.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReminderRepeatCalculatorTest {
    private val calculator = ReminderRepeatCalculator()
    private lateinit var previousZone: TimeZone

    @Before fun setUp() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    }

    @After fun tearDown() { TimeZone.setDefault(previousZone) }

    @Test fun monthly31RecoversAfterShortMonths() {
        val anchor = at(2027, 1, 31)
        val feb = next(anchor, RepeatRule.MONTHLY, anchor)
        val mar = next(feb, RepeatRule.MONTHLY, anchor)
        val apr = next(mar, RepeatRule.MONTHLY, anchor)
        assertEquals(at(2027, 2, 28), feb)
        assertEquals(at(2027, 3, 31), mar)
        assertEquals(at(2027, 4, 30), apr)
        assertEquals(at(2027, 5, 31), next(apr, RepeatRule.MONTHLY, anchor))
    }

    @Test fun monthly30And29RecoverAfterNonLeapFebruary() {
        for (day in listOf(29, 30)) {
            val anchor = at(2027, 1, day)
            val feb = next(anchor, RepeatRule.MONTHLY, anchor)
            assertEquals(at(2027, 2, 28), feb)
            assertEquals(at(2027, 3, day), next(feb, RepeatRule.MONTHLY, anchor))
        }
    }

    @Test fun monthlyUsesLeapFebruaryAndKeepsOrdinaryDay() {
        val lastDay = at(2028, 1, 31)
        assertEquals(at(2028, 2, 29), next(lastDay, RepeatRule.MONTHLY, lastDay))
        val fifteenth = at(2027, 1, 15)
        assertEquals(at(2027, 2, 15), next(fifteenth, RepeatRule.MONTHLY, fifteenth))
    }

    @Test fun monthlyCrossesDecemberAndJanuary() {
        val anchor = at(2026, 12, 31)
        assertEquals(at(2027, 1, 31), next(anchor, RepeatRule.MONTHLY, anchor))
    }

    @Test fun yearlyOrdinaryDateAndLeapDayReturnToOriginalDate() {
        val ordinary = at(2026, 10, 15)
        assertEquals(at(2027, 10, 15), next(ordinary, RepeatRule.YEARLY, ordinary))
        val leap = at(2028, 2, 29)
        val y2029 = next(leap, RepeatRule.YEARLY, leap)
        val y2030 = next(y2029, RepeatRule.YEARLY, leap)
        val y2031 = next(y2030, RepeatRule.YEARLY, leap)
        assertEquals(at(2029, 2, 28), y2029)
        assertEquals(at(2030, 2, 28), y2030)
        assertEquals(at(2031, 2, 28), y2031)
        assertEquals(at(2032, 2, 29), next(y2031, RepeatRule.YEARLY, leap))
    }

    @Test fun calendarTimeAndMinutesSurviveShortMonthsAndYears() {
        val month = at(2027, 1, 31, 22, 47)
        val year = at(2028, 2, 29, 6, 13)
        assertEquals(at(2027, 2, 28, 22, 47), next(month, RepeatRule.MONTHLY, month))
        assertEquals(at(2029, 2, 28, 6, 13), next(year, RepeatRule.YEARLY, year))
    }

    @Test fun reschedulingMissedPeriodsAlwaysSelectsOneFutureOccurrence() {
        val anchor = at(2027, 1, 31)
        val now = at(2027, 6, 15)
        val next = calculator.calculateNextTrigger(anchor, RepeatRule.MONTHLY, now, anchor)
        assertEquals(at(2027, 6, 30), next)
        assertEquals(next, calculator.calculateNextTrigger(anchor, RepeatRule.MONTHLY, now, anchor))
        assertTrue(next!! > now)
        val feb = at(2027, 2, 28)
        assertEquals(at(2027, 3, 31), calculator.calculateNextTrigger(feb, RepeatRule.MONTHLY, feb, anchor))
    }

    @Test fun exactlyAtNextOccurrenceSkipsItButNeverRepeatsIt() {
        val anchor = at(2027, 1, 31)
        val feb = at(2027, 2, 28)
        assertEquals(at(2027, 3, 31), calculator.calculateNextTrigger(anchor, RepeatRule.MONTHLY, feb, anchor))
        assertEquals(at(2027, 3, 31), calculator.calculateNextTrigger(feb, RepeatRule.MONTHLY, feb, anchor))
    }

    @Test fun onceDailyAndWeeklyKeepExistingBehavior() {
        val start = at(2027, 1, 15)
        assertNull(calculator.calculateNextTrigger(start, RepeatRule.NONE, start, start))
        assertEquals(at(2027, 1, 16), next(start, RepeatRule.DAILY, start))
        assertEquals(at(2027, 1, 22), next(start, RepeatRule.WEEKLY, start))
    }

    @Test fun legacyYearlyStillRepeatsAndUnknownMonthlyAnchorIsNotGuessed() {
        assertEquals(at(2029, 2, 28), calculator.calculateNextTrigger(
            at(2028, 2, 29), RepeatRule.YEARLY, at(2028, 2, 29)
        ))
        assertEquals(at(2027, 3, 28), calculator.calculateNextTrigger(
            at(2027, 2, 28), RepeatRule.MONTHLY, at(2027, 2, 28)
        ))
    }

    private fun next(current: Long, rule: RepeatRule, anchor: Long): Long =
        calculator.calculateNextTrigger(current, rule, current, anchor)!!

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
