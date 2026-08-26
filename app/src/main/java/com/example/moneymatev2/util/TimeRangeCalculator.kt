package com.example.moneymatev2.util

import com.example.moneymatev2.ui.viewmodel.HomePeriod
import java.util.Calendar
import kotlin.math.acos

object TimeRangeCalculator {
    fun getTimeRange(period: HomePeriod, anchor: Long): Pair<Long, Long>{
        val cal = Calendar.getInstance().apply { timeInMillis = anchor }
        fun clearTimeOfDay(){
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return when(period){
            HomePeriod.DAY -> {
                clearTimeOfDay()
                val s = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                s to cal.timeInMillis
            }
            HomePeriod.WEEK ->{
                clearTimeOfDay()
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val s = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                s to cal.timeInMillis
            }
            HomePeriod.MONTH -> {
                clearTimeOfDay()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val s = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                s to cal.timeInMillis
            }
            HomePeriod.YEAR -> {
                clearTimeOfDay()
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val s = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                s to cal.timeInMillis
            }
            HomePeriod.PERIOD -> {
                0L to Long.MAX_VALUE
            }
            HomePeriod.CUSTOM -> anchor to (anchor + 24 * 60 * 60 * 1000L)
        }
    }

    fun moveAnchor(period: HomePeriod, anchor: Long, delta: Int): Long{
        val field = when(period){
            HomePeriod.DAY -> Calendar.DAY_OF_YEAR
            HomePeriod.WEEK -> Calendar.WEEK_OF_YEAR
            HomePeriod.MONTH -> Calendar.MONTH
            HomePeriod.YEAR -> Calendar.YEAR
            HomePeriod.PERIOD -> return anchor
            HomePeriod.CUSTOM -> return anchor
        }
        return Calendar.getInstance().apply {
            timeInMillis = anchor
            add(field, delta)
        }.timeInMillis
    }
}