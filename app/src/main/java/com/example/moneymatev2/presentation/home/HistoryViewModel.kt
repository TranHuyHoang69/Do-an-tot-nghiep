package com.example.moneymatev2.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.TimeRangeCalculator
import com.example.moneymatev2.core.util.groupByCategory
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.navigation.HomeNavKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

enum class DetailSortType { DATE_DESC, AMOUNT_DESC }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransactionWithCategoryUseCase: GetTransactionWithCategoryUseCase
) : ViewModel() {

    var selectedPeriod by mutableStateOf(
        savedStateHandle.get<String>(HomeNavKeys.SELECTED_PERIOD)
            ?.let { runCatching { HomePeriod.valueOf(it) }.getOrNull() } ?: HomePeriod.DAY
    )
        private set

    var anchorDate by mutableStateOf(
        savedStateHandle.get<Long>(HomeNavKeys.ANCHOR_DATE) ?: System.currentTimeMillis()
    )
        private set

    var selectedType by mutableStateOf(
        savedStateHandle.get<String>(HomeNavKeys.SELECTED_TYPE)
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            ?: TransactionType.EXPENSE
    )
        private set

    var customRangeStart by mutableStateOf<Long?>(
        if (selectedPeriod == HomePeriod.CUSTOM) anchorDate else null
    )
        private set

    var customRangeEnd by mutableStateOf<Long?>(
        savedStateHandle.get<Long>(HomeNavKeys.CUSTOM_END)?.takeIf { it != -1L }
    )
        private set

    var sortType by mutableStateOf(DetailSortType.DATE_DESC)
        private set

    private val allTransaction = getTransactionWithCategoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _groupedItems = MutableStateFlow<List<GroupedTransaction>>(emptyList())
    val groupedItems: StateFlow<List<GroupedTransaction>> = _groupedItems

    init {
        viewModelScope.launch {
            allTransaction.collect { recompute(it) } // recompute giờ là suspend fun, gọi trực tiếp trong coroutine đang chạy là hợp lệ
        }
    }

    // Chuyển thành suspend + withContext(Dispatchers.Default) -> toàn bộ filter/group/sort
    // chạy trên thread pool nền, không chiếm main thread khi danh sách giao dịch lớn.
    private suspend fun recompute(source: List<TransactionWithCategory>) {
        val result = withContext(Dispatchers.Default) {
            val (start, end) = if (selectedPeriod == HomePeriod.CUSTOM) {
                (customRangeStart ?: anchorDate) to (customRangeEnd ?: (anchorDate + 24 * 60 * 60 * 1000L))
            } else {
                TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
            }

            val inRange = source.filter { it.transaction.createdAt in start until end }
            val grouped = inRange.groupByCategory(selectedType)

            when (sortType) {
                DetailSortType.DATE_DESC -> grouped.sortedByDescending { it.latestTransactionAt }
                DetailSortType.AMOUNT_DESC -> grouped.sortedByDescending { it.totalAmount }
            }
        }
        _groupedItems.value = result
    }

    // Các hàm dưới đây gọi recompute() (suspend) -> phải bọc trong viewModelScope.launch
    // vì onPeriodChange/onTypeChange/... bản thân KHÔNG phải suspend (được gọi trực tiếp từ UI,
    // Compose callback không chấp nhận suspend fun).

    fun onPeriodChange(period: HomePeriod) {
        selectedPeriod = period
        viewModelScope.launch { recompute(allTransaction.value) }
    }

    fun onTypeChange(type: TransactionType) {
        selectedType = type
        viewModelScope.launch { recompute(allTransaction.value) }
    }

    fun moveTimeRange(delta: Int) {
        anchorDate = TimeRangeCalculator.moveAnchor(selectedPeriod, anchorDate, delta)
        viewModelScope.launch { recompute(allTransaction.value) }
    }

    fun onSortTypeChange(type: DetailSortType) {
        sortType = type
        viewModelScope.launch { recompute(allTransaction.value) }
    }

    fun setCustomRange(start: Long, end: Long) {
        selectedPeriod = HomePeriod.CUSTOM
        customRangeStart = start
        customRangeEnd = end + 24 * 60 * 60 * 1000L
        anchorDate = start
        viewModelScope.launch { recompute(allTransaction.value) }
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
            HomePeriod.DAY -> SimpleDateFormat("d / M / yyyy", Locale("vi")).format(Date(anchorDate))
            HomePeriod.WEEK -> {
                val sdf = SimpleDateFormat("d/M", Locale("vi"))
                "${sdf.format(Date(start))} - ${sdf.format(Date(end - 1))}"
            }
            HomePeriod.MONTH -> SimpleDateFormat(" M / yyyy", Locale("vi")).format(Date(anchorDate))
            HomePeriod.YEAR -> SimpleDateFormat("yyyy", Locale("vi")).format(Date(anchorDate))
            else -> "Toàn bộ thời gian"
        }
    }

    fun isNextEnabled(): Boolean {
        if (selectedPeriod == HomePeriod.CUSTOM) return false
        val (_, end) = TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        return end <= System.currentTimeMillis()
    }

    fun getTransactionsInGroup(categoryIdentityKey: String): List<TransactionWithCategory> {
        val (start, end) = if (selectedPeriod == HomePeriod.CUSTOM) {
            (customRangeStart ?: anchorDate) to (customRangeEnd ?: (anchorDate + 24 * 60 * 60 * 1000L))
        } else {
            TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        }
        return allTransaction.value.filter {
            it.transaction.type == selectedType &&
                    it.transaction.createdAt in start until end &&
                    it.category.categoryIdentityKey() == categoryIdentityKey
        }
    }
}