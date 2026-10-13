package com.example.moneymatev2.domain.usecase.recurring

import com.example.moneymatev2.domain.model.RecurringFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Calculate each occurrence from the ORIGINAL anchor date, not the last due date.
 * E.g. monthly Jan 31 -> Feb 28 -> Mar 31 (no day-of-month drift).
 * Android minSdk 26 supports java.time without desugaring.
 */
class RecurringScheduleCalculator @Inject constructor() {

    fun occurrenceAt(
        startAt: Long,
        timeZoneId: String,
        frequency: RecurringFrequency,
        occurrenceIndex: Long
    ): Long {
        require(occurrenceIndex >= 0L) { "Occurrence index must be nonnegative" }
        val zone = ZoneId.of(timeZoneId)
        val anchor = Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate()
        val date = when (frequency) {
            RecurringFrequency.DAILY -> anchor.plusDays(occurrenceIndex)
            RecurringFrequency.WEEKLY -> anchor.plusWeeks(occurrenceIndex)
            RecurringFrequency.EVERY_TWO_WEEKS -> anchor.plusWeeks(Math.multiplyExact(occurrenceIndex, 2L))
            RecurringFrequency.EVERY_FOUR_WEEKS -> anchor.plusWeeks(Math.multiplyExact(occurrenceIndex, 4L))
            RecurringFrequency.MONTHLY -> anchor.plusMonths(occurrenceIndex)
            RecurringFrequency.EVERY_TWO_MONTHS -> anchor.plusMonths(Math.multiplyExact(occurrenceIndex, 2L))
            RecurringFrequency.QUARTERLY -> anchor.plusMonths(Math.multiplyExact(occurrenceIndex, 3L))
            RecurringFrequency.EVERY_SIX_MONTHS -> anchor.plusMonths(Math.multiplyExact(occurrenceIndex, 6L))
            RecurringFrequency.YEARLY -> anchor.plusYears(occurrenceIndex)
        }
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Material DatePicker returns a UTC midnight date. Convert its DATE, not its instant. */
    fun datePickerMillisToLocalStart(
        selectedDateUtcMillis: Long,
        timeZoneId: String
    ): Long {
        val date = Instant.ofEpochMilli(selectedDateUtcMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return date.atStartOfDay(ZoneId.of(timeZoneId)).toInstant().toEpochMilli()
    }

    fun localDateToStart(date: LocalDate, timeZoneId: String): Long =
        date.atStartOfDay(ZoneId.of(timeZoneId)).toInstant().toEpochMilli()
}
