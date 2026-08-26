package com.example.moneymatev2.ui.viewmodel

import android.icu.util.Calendar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.util.TimeRangeCalculator
import com.example.moneymatev2.util.groupByCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


enum class HomePeriod {
    DAY, WEEK, MONTH, YEAR, PERIOD, CUSTOM
}

enum class DetailPeriod { DATE, AMOUNT }

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val totalIncome: Long,
        val totalExpense: Long,
        val chartData: List<GroupedTransaction>
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTransactionWithCategoryUseCase: GetTransactionWithCategoryUseCase
) : ViewModel() {
    var selectedPeriod by mutableStateOf(HomePeriod.DAY)
        private set
    var selectedType by mutableStateOf(TransactionType.EXPENSE)
        private set
    var anchorDate by mutableStateOf(System.currentTimeMillis())
        private set
    private val allTransactions =
        getTransactionWithCategoryUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _transactionState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val transactionState: StateFlow<HomeUiState> = _transactionState
    private val _totalBalance = MutableStateFlow(0L)
    val totalBalance: StateFlow<Long> = _totalBalance
    var customRangeStart by mutableStateOf<Long?>(null)
        private set
    var customRangeEnd by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            allTransactions.collect { recompute(it) }
        }
    }

    private suspend fun recompute(list: List<TransactionWithCategory>) {
        _transactionState.value = HomeUiState.Loading
        withContext(Dispatchers.Default) {
            try {
                val (start, end) = if (selectedPeriod == HomePeriod.CUSTOM) {
                    (customRangeStart ?: anchorDate) to (customRangeEnd ?: (anchorDate + 24 * 60 * 60 * 1000L))
                } else {
                    TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
                }
                val inRange = list.filter { it.transaction.createdAt in start until end }

                val totalIncome = inRange
                    .filter { it.transaction.type == TransactionType.INCOME }
                    .sumOf { it.transaction.money.amountMinor }
                val totalExpense = inRange
                    .filter { it.transaction.type == TransactionType.EXPENSE }
                    .sumOf { it.transaction.money.amountMinor }

                _totalBalance.value = list.sumOf {
                    if (it.transaction.type == TransactionType.INCOME) it.transaction.money.amountMinor
                    else -it.transaction.money.amountMinor
                }

                _transactionState.value = HomeUiState.Success(
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    chartData = inRange.groupByCategory(selectedType)
                )
            } catch (e: Exception) {
                _transactionState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun moveTimeRange(delta: Int) {
        anchorDate = TimeRangeCalculator.moveAnchor(selectedPeriod, anchorDate, delta)
        viewModelScope.launch {
            recompute(allTransactions.value)
        }
    }

    fun onPeriodChange(period: HomePeriod) {
        selectedPeriod = period
        viewModelScope.launch {
            recompute(allTransactions.value)
        }
    }

    fun onTypeChange(type: TransactionType) {
        selectedType = type
        viewModelScope.launch {
            recompute(allTransactions.value)
        }
    }

    fun getDisplayTime(): String {
        if (selectedPeriod == HomePeriod.CUSTOM) {
            val sdf = SimpleDateFormat("d/M/yyyy", Locale("vi"))
            val start = customRangeStart ?: anchorDate
            val end = (customRangeEnd ?: (anchorDate + 24 * 60 * 60 * 1000L)) - 1
            return "${sdf.format(Date(start))} - ${sdf.format(Date(end))}"
        }
        val (start, end) = TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        return when (selectedPeriod) {
            HomePeriod.DAY -> SimpleDateFormat("d 'thg' M, yyyy", Locale("vi")).format(Date(anchorDate))
            HomePeriod.WEEK -> {
                val sdf = SimpleDateFormat("d/M", Locale("vi"))
                "${sdf.format(Date(start))} - ${sdf.format(Date(end - 1))}"
            }
            HomePeriod.MONTH -> SimpleDateFormat("'Tháng' M, yyyy", Locale("vi")).format(Date(anchorDate))
            HomePeriod.YEAR -> SimpleDateFormat("yyyy", Locale("vi")).format(Date(anchorDate))
            else -> "Toàn bộ thời gian"
        }
    }

    fun isNextEnabled(): Boolean {
        if (selectedPeriod == HomePeriod.CUSTOM) return false // không có "Trước/Sau" cho khoảng tùy ý
        val (_, end) = TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        return end <= System.currentTimeMillis()
    }

    fun setCustomRange(start: Long, end: Long){
        selectedPeriod = HomePeriod.CUSTOM
        customRangeStart = start
        customRangeEnd = end + 24 * 60 * 60 * 1000L
        anchorDate = start
        viewModelScope.launch { recompute(allTransactions.value) }
    }
}