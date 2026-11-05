package com.example.moneymatev2.presentation.home

import com.example.moneymatev2.ui.item.categoryDisplayName

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey
import androidx.core.graphics.toColorInt
import com.example.moneymatev2.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onTransactionClick: (String) -> Unit = {}
) {
    val groupedItems by viewModel.groupedItems.collectAsState()
    val searchOpen by viewModel.searchOpen.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val hasTransactionsInPeriod by viewModel.hasTransactionsInPeriod.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val localizedDefaultNames = mapOf(
        R.string.statistics_default_food to stringResource(R.string.statistics_default_food),
        R.string.statistics_default_shopping to stringResource(R.string.statistics_default_shopping),
        R.string.statistics_default_health to stringResource(R.string.statistics_default_health),
        R.string.statistics_default_transport to stringResource(R.string.statistics_default_transport),
        R.string.statistics_default_gift to stringResource(R.string.statistics_default_gift),
        R.string.statistics_default_entertainment to stringResource(R.string.statistics_default_entertainment),
        R.string.statistics_default_other to stringResource(R.string.statistics_default_other),
        R.string.statistics_default_salary to stringResource(R.string.statistics_default_salary)
    )
    LaunchedEffect(localizedDefaultNames) { viewModel.onSearchCategoryLabelsChange(localizedDefaultNames) }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val closeSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.closeSearch()
    }
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    BackHandler(enabled = searchOpen) {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else closeSearch()
    }

    val expenseColor = MaterialTheme.colorScheme.error
    val incomeColor = MaterialTheme.colorScheme.primary
    val themeColor = if (viewModel.selectedType == TransactionType.EXPENSE) expenseColor else incomeColor

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomRange(start, end)
                    }
                    showDatePicker = false
                }) {
                    Text(text = stringResource(StringRes.confirm), color = themeColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(StringRes.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            text = stringResource(StringRes.select_period),
                            modifier = Modifier.padding(16.dp)
                        )
                    },
                    showModeToggle = false
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(56.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Đổi onOpenDrawer -> onBack: app không dùng Navigation Drawer,
                    // History là 1 destination độc lập, cần mũi tên quay lại.
                    if (searchOpen) {
                        IconButton(onClick = closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.history_search_back),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.history_search_hint), maxLines = 1) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent)
                        )
                        IconButton(onClick = { viewModel.onSearchQueryChange("") },
                            enabled = searchQuery.isNotEmpty()) {
                            Icon(Icons.Default.Close,
                                contentDescription = stringResource(R.string.history_search_clear),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.profile_menu),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(StringRes.history_title),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::openSearch) {
                            Icon(Icons.Default.Search,
                                contentDescription = stringResource(R.string.history_search_open),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {

            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf(
                        TransactionType.EXPENSE to stringResource(StringRes.expense),
                        TransactionType.INCOME to stringResource(StringRes.income)
                    ).forEach { (type, label) ->
                        val isSelected = viewModel.selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) themeColor else Color.Transparent)
                                .clickable { viewModel.onTypeChange(type) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            CompactTimeNavigation(
                currentMode = viewModel.selectedPeriod,
                displayTime = viewModel.getDisplayTime(),
                isNextEnabled = viewModel.isNextEnabled(),
                themeColor = themeColor,
                onModeChange = { viewModel.onPeriodChange(it) },
                onPrevious = { viewModel.moveTimeRange(-1) },
                onNext = { viewModel.moveTimeRange(1) },
                onRangeClick = { showDatePicker = true }
            )

            if (searchOpen && searchQuery.isBlank()) {
                Text(
                    text = stringResource(R.string.history_search_guide),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!isLoading && !loadError && searchError == null) SummaryRow(
                totalAmount = groupedItems.sumOf { it.totalAmount },
                themeColor = themeColor,
                currentSortType = viewModel.sortType,
                onSortChange = { viewModel.onSortTypeChange(it) }
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else if (loadError) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.history_search_load_error),
                        color = MaterialTheme.colorScheme.error)
                }
            } else if (searchError != null) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(if (searchError == HistorySearchQuery.InvalidAmountRange)
                        R.string.history_search_invalid_range else R.string.history_search_invalid_amount),
                        color = MaterialTheme.colorScheme.error)
                }
            } else if (groupedItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = stringResource(if (searchQuery.isNotBlank() && hasTransactionsInPeriod)
                            R.string.history_search_no_results else StringRes.dont_have_transaction),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = groupedItems,
                        key = { _, group -> group.category.categoryIdentityKey() ?: group.category.id }
                    ) { _, group ->
                        ExpenseItem(
                            group = group,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    expandedGroupKey = group.category.categoryIdentityKey() ?: group.category.id
                                }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(groupedItems, isLoading) {
        if (!isLoading && expandedGroupKey != null && groupedItems.none {
                (it.category.categoryIdentityKey() ?: it.category.id) == expandedGroupKey
            }) expandedGroupKey = null
    }
    expandedGroupKey?.let { key ->
        val transactions = remember(key, groupedItems) { viewModel.getTransactionsInGroup(key) }
        TransactionDetailDialog(transactions = transactions, onDismiss = { expandedGroupKey = null },
            onTransactionClick = { id -> expandedGroupKey = null; onTransactionClick(id) })
    }
}

@Composable
fun CompactTimeNavigation(
    currentMode: HomePeriod,
    displayTime: String,
    isNextEnabled: Boolean,
    themeColor: Color,
    onModeChange: (HomePeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRangeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                HomePeriod.DAY to stringResource(StringRes.day),
                HomePeriod.WEEK to stringResource(StringRes.week),
                HomePeriod.MONTH to stringResource(StringRes.month),
                HomePeriod.YEAR to stringResource(StringRes.year)
            )
            modes.forEach { (mode, label) ->
                val isSelected = currentMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = isSelected, role = Role.Tab) { onModeChange(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = currentMode == HomePeriod.CUSTOM, role = Role.Tab) { onRangeClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(StringRes.period),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    color = if (currentMode == HomePeriod.CUSTOM) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (currentMode == HomePeriod.CUSTOM) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val isCustom = currentMode == HomePeriod.CUSTOM
            IconButton(onClick = onPrevious, enabled = !isCustom) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.history_previous_period),
                    tint = if (isCustom) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else themeColor
                )
            }
            Text(
                text = displayTime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            val nextState = isNextEnabled && !isCustom
            IconButton(onClick = onNext, enabled = nextState) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.history_next_period),
                    tint = if (nextState) themeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun SummaryRow(
    totalAmount: Long,
    themeColor: Color,
    currentSortType: DetailSortType,
    onSortChange: (DetailSortType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sortTimeLabel = stringResource(StringRes.sort_by_time)
    val sortAmountLabel = stringResource(StringRes.sort_by_amount)
    val currentLabel = if (currentSortType == DetailSortType.DATE_DESC) sortTimeLabel else sortAmountLabel

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(StringRes.total),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${CurrencyFormatter.formatCompact(totalAmount)} đ",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = themeColor
            )
        }
        Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(StringRes.sort_by)} $currentLabel",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(sortTimeLabel) },
                    onClick = { onSortChange(DetailSortType.DATE_DESC); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(sortAmountLabel) },
                    onClick = { onSortChange(DetailSortType.AMOUNT_DESC); expanded = false }
                )
            }
        }
    }
}

@Composable
fun ExpenseItem(
    group: GroupedTransaction,
    modifier: Modifier = Modifier
) {
    val categoryColor = remember(group.category.colorHex) {
        runCatching { Color(group.category.colorHex.toColorInt()) }.getOrDefault(Color.Gray)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = if (group.category.isMissing) androidx.compose.ui.res.stringResource(com.example.moneymatev2.R.string.category_missing_reference) else categoryDisplayName(group.category), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "${group.transactionCount} ${stringResource(StringRes.transaction)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val sign = if (group.type == TransactionType.EXPENSE) "-" else "+"
            Text(
                text = "$sign ${CurrencyFormatter.formatCompact(group.totalAmount)} đ",
                fontWeight = FontWeight.Bold,
                color = if (group.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    transactions: List<TransactionWithCategory>,
    onDismiss: () -> Unit,
    onTransactionClick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(transactions, key = { _, item -> item.transaction.id }) { _, item ->
                    val sdf = remember { SimpleDateFormat("d/M/yyyy", Locale("vi")) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onTransactionClick(item.transaction.id) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = sdf.format(Date(item.transaction.createdAt)), style = MaterialTheme.typography.bodySmall)
                            if (!item.transaction.note.isNullOrBlank()) {
                                Text(text = item.transaction.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val sign = if (item.transaction.type == TransactionType.EXPENSE) "-" else "+"
                        Text(
                            text = "$sign ${CurrencyFormatter.formatCompact(item.transaction.money.amountMinor)} đ",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
