package com.example.moneymatev2.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.TimeRangeCalculator
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.navigation.HomeNavKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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

    private var latestTransactions: List<TransactionWithCategory> = emptyList()
    private var sourceReady = false
    private var recomputeJob: Job? = null
    private var localizedDefaultNames: Map<Int, String> = emptyMap()

    private val _groupedItems = MutableStateFlow<List<GroupedTransaction>>(emptyList())
    val groupedItems: StateFlow<List<GroupedTransaction>> = _groupedItems
    private val _visibleTransactions = MutableStateFlow<List<TransactionWithCategory>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError
    private val _hasTransactionsInPeriod = MutableStateFlow(false)
    val hasTransactionsInPeriod: StateFlow<Boolean> = _hasTransactionsInPeriod
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen
    private val _searchError = MutableStateFlow<HistorySearchQuery?>(null)
    val searchError: StateFlow<HistorySearchQuery?> = _searchError

    init {
        viewModelScope.launch {
            getTransactionWithCategoryUseCase().catch {
                recomputeJob?.cancel()
                sourceReady = false
                latestTransactions = emptyList()
                _visibleTransactions.value = emptyList()
                _groupedItems.value = emptyList()
                _hasTransactionsInPeriod.value = false
                _loadError.value = true
                _isLoading.value = false
            }.collect { source ->
                latestTransactions = source
                sourceReady = true
                _loadError.value = false
                scheduleRecompute()
            }
        }
    }

    private fun scheduleRecompute(debounceMs: Long = 0) {
        if (!sourceReady) return
        val source = latestTransactions
        val filters = HistorySearchFilters(selectedPeriod, anchorDate, selectedType, customRangeStart,
            customRangeEnd, sortType, _searchQuery.value, localizedDefaultNames)
        recomputeJob?.cancel()
        _isLoading.value = true
        recomputeJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val result = withContext(Dispatchers.Default) { HistorySearchEngine.search(source, filters) }
            _visibleTransactions.value = result.transactions
            _groupedItems.value = result.groups
            _hasTransactionsInPeriod.value = result.hasPeriodTransactions
            _searchError.value = result.error
            _isLoading.value = false
        }
    }

    fun openSearch() { _searchOpen.value = true }

    fun closeSearch() {
        _searchOpen.value = false
        _searchQuery.value = ""
        scheduleRecompute()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        scheduleRecompute(debounceMs = if (query.isBlank()) 0 else 120)
    }

    fun onSearchCategoryLabelsChange(labels: Map<Int, String>) {
        if (localizedDefaultNames == labels) return
        localizedDefaultNames = labels
        scheduleRecompute()
    }

    fun onPeriodChange(period: HomePeriod) {
        selectedPeriod = period
        scheduleRecompute()
    }

    fun onTypeChange(type: TransactionType) {
        selectedType = type
        scheduleRecompute()
    }

    fun moveTimeRange(delta: Int) {
        anchorDate = TimeRangeCalculator.moveAnchor(selectedPeriod, anchorDate, delta)
        scheduleRecompute()
    }

    fun onSortTypeChange(type: DetailSortType) {
        sortType = type
        scheduleRecompute()
    }

    fun setCustomRange(start: Long, end: Long) {
        selectedPeriod = HomePeriod.CUSTOM
        customRangeStart = start
        customRangeEnd = end + 24 * 60 * 60 * 1000L
        anchorDate = start
        scheduleRecompute()
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

    fun getTransactionsInGroup(categoryIdentityKey: String): List<TransactionWithCategory> =
        _visibleTransactions.value.filter { it.category.categoryIdentityKey() == categoryIdentityKey }
}
