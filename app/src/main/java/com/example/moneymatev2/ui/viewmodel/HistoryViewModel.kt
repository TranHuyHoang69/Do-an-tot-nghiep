package com.example.moneymatev2.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.navigation.HomeNavKeys
import com.example.moneymatev2.util.TimeRangeCalculator
import com.example.moneymatev2.util.groupByCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransactionWithCategoryUseCase: GetTransactionWithCategoryUseCase
): ViewModel(){
    var selectedPeriod by mutableStateOf(
        savedStateHandle.get<String>(HomeNavKeys.SELECTED_TYPE)
            ?.let { runCatching { HomePeriod.valueOf(it) }.getOrNull() } ?: HomePeriod.DAY
    )
        private set
    var anchorDate by mutableStateOf(
        savedStateHandle.get<Long>(HomeNavKeys.ANCHOR_DATE) ?: System.currentTimeMillis()
    )
        private set
    var selectedType by mutableStateOf(
        savedStateHandle.get<String>(HomeNavKeys.SELECTED_TYPE)
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.EXPENSE
    )
        private set

    private val allTransaction = getTransactionWithCategoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _groupItems = MutableStateFlow<List<GroupedTransaction>>(emptyList())
    val groupItems: StateFlow<List<GroupedTransaction>> = _groupItems

    var customRangeStart by mutableStateOf<Long?>(
        if(selectedPeriod == HomePeriod.CUSTOM) anchorDate else null
    )
        private set
    var customRangeEnd by mutableStateOf<Long?>(
        savedStateHandle.get<Long>(HomeNavKeys.CUSTOM_END)?.takeIf { it != -1L }
    )
        private set

    init{
        viewModelScope.launch {
            allTransaction.collect {
                recompute(it)
            }
        }
    }

    private fun recompute(source: List<TransactionWithCategory>) {
        val (start, end) = if(selectedPeriod == HomePeriod.CUSTOM){
            (customRangeStart ?: anchorDate) to (customRangeEnd ?: (anchorDate + 24 * 60 * 60 * 1000L))
        }else{
            TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        }

        val inRange = source.filter { it.transaction.createdAt in start until end }
        _groupItems.value = inRange.groupByCategory(selectedType)
    }

    fun onPeriodChange(period: HomePeriod){
        selectedPeriod = period
        recompute(allTransaction.value)
    }

    fun onTypeChange(type: TransactionType){
        selectedType = type
        recompute(allTransaction.value)
    }

    fun moveTimeRange(delta: Int){
        anchorDate = TimeRangeCalculator.moveAnchor(selectedPeriod, anchorDate, delta)
        recompute(allTransaction.value)
    }

    fun getTransactionInGroup(categoryIdentityKey: String): List<TransactionWithCategory>{
        val (start, end) = TimeRangeCalculator.getTimeRange(selectedPeriod, anchorDate)
        return allTransaction.value.filter {
            it.transaction.type == selectedType &&
                    it.transaction.createdAt in start until end &&
                    it.category.categoryIdentityKey() == categoryIdentityKey
        }
    }
}