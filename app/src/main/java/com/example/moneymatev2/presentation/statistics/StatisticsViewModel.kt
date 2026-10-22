package com.example.moneymatev2.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface StatisticsState {
    data object Loading : StatisticsState
    data class Ready(val snapshot: StatisticsSnapshot, val monthly: List<StatisticsBucket>, val ownerId: String?) : StatisticsState
    data object Error : StatisticsState
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getTransactions: GetTransactionWithCategoryUseCase,
    authRepository: AuthRepository
) : ViewModel() {
    private val initialRange = savedStateHandle.get<Long>("start")?.let { start ->
        savedStateHandle.get<Long>("end")?.let { end -> runCatching { StatisticsRange(start, end) }.getOrNull() }
    } ?: StatisticsDates.forPeriod(StatisticsPeriod.THIS_MONTH)

    private val _range = MutableStateFlow(initialRange)
    val range: StateFlow<StatisticsRange> = _range
    private val _period = MutableStateFlow(if (savedStateHandle.get<Long>("start") != null) StatisticsPeriod.CUSTOM else StatisticsPeriod.THIS_MONTH)
    val period: StateFlow<StatisticsPeriod> = _period
    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year

    val state: StateFlow<StatisticsState> = combine(
        getTransactions(), authRepository.observeAuthState(), _range, _year
    ) { source, user, range, year ->
        val owned: List<TransactionWithCategory> = if (user == null) emptyList() else source.filter { it.transaction.userId == user.userId }
        Triple(owned, range, year) to user?.userId
    }.map { (input, owner) ->
        val (owned, range, year) = input
        StatisticsState.Ready(StatisticsCalculator.calculate(owned, range), StatisticsCalculator.monthly(owned, year), owner) as StatisticsState
    }.flowOn(Dispatchers.Default).catch { emit(StatisticsState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsState.Loading)

    fun apply(period: StatisticsPeriod, custom: StatisticsRange? = null) {
        _range.value = if (period == StatisticsPeriod.CUSTOM) requireNotNull(custom) else StatisticsDates.forPeriod(period)
        _period.value = period
    }

    fun setRange(range: StatisticsRange) {
        _range.value = range
        _period.value = StatisticsPeriod.CUSTOM
    }

    fun changeYear(delta: Int) { _year.value = (_year.value + delta).coerceIn(1970, 9998) }
}
