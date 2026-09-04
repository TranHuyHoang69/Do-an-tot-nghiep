package com.example.moneymatev2.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.categoryIdentityKey
import com.example.moneymatev2.presentation.theme.AppTopBarColor
import com.example.moneymatev2.presentation.theme.StringResource
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import androidx.core.graphics.toColorInt
import com.example.moneymatev2.ui.components.MorphingChartSection


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onSeeMoreDetail: (period: String, anchorDate: Long, type: TransactionType, customEnd: Long) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val transactionState by viewModel.transactionState.collectAsState()
    val totalBalance by viewModel.totalBalance.collectAsState()

    val expenseColor = MaterialTheme.colorScheme.primary
    val incomeColor = MaterialTheme.colorScheme.secondary
    val themeColor = remember(viewModel.selectedType) {
        if (viewModel.selectedType == TransactionType.EXPENSE) expenseColor else incomeColor
    }


    val scrollState = rememberLazyListState()

    val density = LocalDensity.current
    val maxMorpScrollPx = remember(density) { with(density) {200.dp.toPx()} }

    val morphProgress by remember {
        derivedStateOf {
            if(scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset / maxMorpScrollPx).coerceIn(0f, 1f)
        }
    }
    val dynamicHeight = lerp(200.dp, 70.dp, morphProgress)

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTopBarColor)
    ) {
        HeaderSection(
            selectedType = viewModel.selectedType,
            totalBalance = totalBalance,
            currencyUnit = "đ",
            onTabSelected = { viewModel.onTypeChange(it) },
            onMenuClick = onMenuClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 250.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            TimeNavigationHeader(
                selectedPeriod = viewModel.selectedPeriod,
                displayTime = viewModel.getDisplayTime(),
                themeColor = themeColor,
                isNextEnabled = viewModel.isNextEnabled(),
                onPeriodChange = { viewModel.onPeriodChange(it) },
                onPrevious = { viewModel.moveTimeRange(-1) },
                onNext = { viewModel.moveTimeRange(1) }
            )

            val chartData = (transactionState as? HomeUiState.Success)?.chartData ?: emptyList()

            Box(modifier = Modifier.fillMaxSize()) {

                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    item(contentType = "Spacer") { Spacer(Modifier.height(180.dp)) }

                    when (val state = transactionState) {
                        is HomeUiState.Loading -> {
                            item(contentType = "Loading") { LoadingUI(themeColor) }
                        }

                        is HomeUiState.Error -> {
                            item(contentType = "Error") { ErrorSection(state.message) }
                        }

                        is HomeUiState.Success -> {
                            if (state.chartData.isEmpty()) {
                                item(contentType = "Empty") { EmptyStateCollection() }
                            } else {
                                items(
                                    items = state.chartData,
                                    key = { it.category.categoryIdentityKey() ?: it.category.id },
                                    contentType = { "TransactionItem" }
                                ) { group ->
                                    TransactionListItem(
                                        group = group,
                                        currencyUnit = "đ",
                                        onClick = {
                                            onSeeMoreDetail(
                                                viewModel.selectedPeriod.name,
                                                viewModel.anchorDate,
                                                viewModel.selectedType,
                                                viewModel.customRangeEnd ?: -1L
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                MorphingChartSection(
                    chartData = chartData,
                    morphProgress = morphProgress,
                    dynamicHeight = dynamicHeight
                )
            }
        }
        FloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Transaction",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if(showDatePicker){
        DatePickerDialog(
            onDismissRequest = {showDatePicker = false},
             confirmButton = {
                 TextButton(
                     onClick = {
                         val start = dateRangePickerState.selectedStartDateMillis
                         val end = dateRangePickerState.selectedEndDateMillis
                         if(start != null && end != null){
                             viewModel.setCustomRange(start, end)
                         }
                         showDatePicker = false
                     }
                 ) {
                     Text(
                         text = StringResource(StringRes.confirm),
                         color = themeColor,
                         fontWeight = FontWeight.Bold
                     )
                 }
             },
            dismissButton = {
                TextButton(
                    onClick = {showDatePicker = false}
                ) {
                    Text(
                        text = StringResource(StringRes.cancel),
                        color = themeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ) {
            Box(
                modifier = Modifier.height(450.dp)
            ){
                DateRangePicker(
                    state = dateRangePickerState,
                    title = {
                        Text(
                            text = StringResource(StringRes.select_period),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ErrorSection(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(50.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun HeaderSection(
    selectedType: TransactionType,
    totalBalance: Long,
    currencyUnit: String,
    onTabSelected: (TransactionType) -> Unit,
    onMenuClick: () -> Unit
) {
    val balanceFormatter = remember {
        DecimalFormat("#,###", DecimalFormatSymbols().apply { groupingSeparator = '.' })
    }
    val formattedBalance = if (totalBalance == 0L) "0" else balanceFormatter.format(totalBalance)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        IconButton(
            onClick = onMenuClick
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = StringResource(StringRes.total_balance),
                fontSize = 12.sp,
                color = Color.White
            )

            Text(
                text = "$formattedBalance $currencyUnit",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
                .padding(4.dp)
        ) {
            listOf(
                TransactionType.EXPENSE to StringResource(StringRes.expense),
                TransactionType.INCOME to StringResource(StringRes.income)
            ).forEach { (type, title) ->
                val isSelected = selectedType == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(0.2f)
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(type) }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

}

@Composable
fun TimeNavigationHeader(
    selectedPeriod: HomePeriod,
    displayTime: String,
    themeColor: Color,
    isNextEnabled: Boolean,
    onPeriodChange: (HomePeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val visibleModes = listOf(HomePeriod.DAY, HomePeriod.WEEK, HomePeriod.MONTH, HomePeriod.YEAR)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            visibleModes.forEach { mode ->
                val isSelected = selectedPeriod == mode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onPeriodChange(mode) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = when (mode) {
                            HomePeriod.DAY -> StringResource(StringRes.day)
                            HomePeriod.WEEK -> StringResource(StringRes.week)
                            HomePeriod.MONTH -> StringResource(StringRes.month)
                            HomePeriod.YEAR -> StringResource(StringRes.year)
                            HomePeriod.PERIOD -> ""
                            HomePeriod.CUSTOM -> StringResource(StringRes.period)
                        },
                        color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp, 2.dp)
                                .background(themeColor, CircleShape)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onPrevious
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous",
                    tint = themeColor
                )
            }

            Text(
                text = displayTime,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(
                onClick = onNext,
                enabled = isNextEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next",
                    tint = if (isNextEnabled) themeColor else themeColor.copy(0.3f)
                )
            }
        }
    }
}


@Composable
fun TransactionListItem(
    group: GroupedTransaction,
    currencyUnit: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            runCatching { Color(group.category.colorHex.toColorInt()) }
                                .getOrDefault(Color.Gray)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = group.category.name,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${group.transactionCount} ${StringResource(StringRes.transaction)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val sign = if (group.type == TransactionType.EXPENSE) "-" else "+"
            Text(
                text = "$sign ${String.format("%,d", group.totalAmount)} $currencyUnit",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyStateCollection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "info",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = StringResource(StringRes.dont_have_transaction),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun LoadingUI(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(50.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = color)
    }
}