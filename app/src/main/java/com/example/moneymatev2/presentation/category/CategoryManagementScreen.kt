package com.example.moneymatev2.presentation.category

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.ui.item.rememberCategoryIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManagementCategoryScreen(
    viewModel: CategoryManagementViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onAddCategoryClick: (TransactionType) -> Unit,
    onCategoryClick: ((CategoryModel) -> Unit)? = null // null = chế độ quản lý (long-press xóa), non-null = chế độ chọn
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deletePreview by viewModel.deletePreview.collectAsState()
    val isManageMode = onCategoryClick == null

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = remember { listOf(StringRes.expense, StringRes.income) }
    val snackbar = remember { SnackbarHostState() }
    val successMessage = stringResource(StringRes.category_delete_success)
    val failedMessage = stringResource(StringRes.category_delete_failed)
    val invalidMessage = stringResource(StringRes.category_delete_invalid)
    val inUseMessage = stringResource(StringRes.category_delete_in_use)
    val loginMessage = stringResource(StringRes.error_not_authenticated)
    val replacementRequiredMessage = stringResource(StringRes.category_delete_changed_need_replacement)

    // --- State cho luồng xóa (chỉ dùng khi isManageMode) ---
    var categoryPendingDelete by remember { mutableStateOf<CategoryModel?>(null) } // đang hiện dialog cảnh báo
    var showReplacementPicker by remember { mutableStateOf(false) } // đang hiện dialog chọn category thay thế
    var selectedReplacement by remember { mutableStateOf<CategoryModel?>(null) }
    var deleteFailure by remember { mutableStateOf<TransactionError?>(null) }
    val closeDeleteDialog = {
        if (!isDeleting) {
            categoryPendingDelete = null
            showReplacementPicker = false
            selectedReplacement = null
            deleteFailure = null
            viewModel.cancelCategoryDeletion()
        }
    }

    LaunchedEffect(viewModel, successMessage, failedMessage, invalidMessage, inUseMessage,
        loginMessage, replacementRequiredMessage) {
        viewModel.deleteEvents.collect { event ->
            when (event) {
                is CategoryManagementViewModel.DeleteCategoryEvent.Success -> {
                    showReplacementPicker = false
                    categoryPendingDelete = null
                    selectedReplacement = null
                    deleteFailure = null
                    viewModel.cancelCategoryDeletion()
                    scope.launch { snackbar.showSnackbar(successMessage) }
                }
                is CategoryManagementViewModel.DeleteCategoryEvent.Failed -> {
                    deleteFailure = event.error
                    if (event.error == TransactionError.CategoryReplacementRequired ||
                        event.error == TransactionError.CategoryInUse ||
                        event.error == TransactionError.CategoryNotSelected ||
                        event.error == TransactionError.NotAuthenticated) {
                        showReplacementPicker = false
                        categoryPendingDelete?.let(viewModel::inspectCategoryDeletion)
                    }
                    val message = when (event.error) {
                        TransactionError.CategoryInUse -> inUseMessage
                        TransactionError.CategoryReplacementRequired -> replacementRequiredMessage
                        TransactionError.CategoryNotSelected -> invalidMessage
                        TransactionError.NotAuthenticated -> loginMessage
                        else -> failedMessage
                    }
                    scope.launch { snackbar.showSnackbar(message) }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(56.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(StringRes.category_management),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, resId ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = stringResource(resId).uppercase(),
                                color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val currentList = if (page == 0) expenseCategories else incomeCategories
                val currentType = if (page == 0) TransactionType.EXPENSE else TransactionType.INCOME

                CategoryGrid(
                    categories = currentList,
                    isManageMode = isManageMode,
                    onTap = { category -> onCategoryClick?.invoke(category) },
                    onLongPress = { category ->
                        deleteFailure = null
                        selectedReplacement = null
                        showReplacementPicker = false
                        categoryPendingDelete = category
                        viewModel.inspectCategoryDeletion(category)
                    },
                    onAddClick = { onAddCategoryClick(currentType) }
                )
            }
        }
    }

    // --- Dialog 1: cảnh báo trước khi xóa ---
    categoryPendingDelete?.let { category ->
        if (!showReplacementPicker) {
            val ready = (deletePreview as? CategoryDeletePreview.Ready)
                ?.takeIf { it.userId == category.userId && it.categoryId == category.id }
            val previewFailed = (deletePreview as? CategoryDeletePreview.Failed)
                ?.takeIf { it.userId == category.userId && it.categoryId == category.id }
            AlertDialog(
                onDismissRequest = closeDeleteDialog,
                title = { Text(stringResource(StringRes.category_delete_title)) },
                text = {
                    Column {
                        when {
                            ready?.usage?.isBlocked == true -> Text(stringResource(when {
                                ready.usage.hasActiveBudget && ready.usage.hasRecurringTransaction ->
                                    StringRes.category_delete_in_budget_and_recurring
                                ready.usage.hasActiveBudget -> StringRes.category_delete_in_budget
                                else -> StringRes.category_delete_in_recurring
                            }))
                            ready != null && ready.usage.transactionCount > 0 -> Text(
                                stringResource(StringRes.category_delete_transactions_warning,
                                    ready.usage.transactionCount)
                            )
                            ready != null -> Text(stringResource(StringRes.category_delete_confirm_empty))
                            previewFailed != null -> Text(
                                stringResource(when (previewFailed.error) {
                                    TransactionError.NotAuthenticated -> StringRes.error_not_authenticated
                                    TransactionError.CategoryNotSelected -> StringRes.category_delete_invalid
                                    else -> StringRes.category_delete_failed
                                }),
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(StringRes.category_delete_loading))
                            }
                        }
                        if (ready != null && deleteFailure != null && !ready.usage.isBlocked) {
                            val messageRes = when (deleteFailure) {
                                TransactionError.CategoryInUse -> StringRes.category_delete_in_use
                                TransactionError.CategoryReplacementRequired -> StringRes.category_delete_changed_need_replacement
                                TransactionError.CategoryNotSelected -> StringRes.category_delete_invalid
                                TransactionError.NotAuthenticated -> StringRes.error_not_authenticated
                                else -> StringRes.category_delete_failed
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(messageRes),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    when {
                        previewFailed != null -> TextButton(onClick = {
                            viewModel.inspectCategoryDeletion(category)
                        }, enabled = !isDeleting) {
                            Text(stringResource(StringRes.category_delete_retry))
                        }
                        ready != null && !ready.usage.isBlocked -> TextButton(onClick = {
                            deleteFailure = null
                            if (ready.usage.transactionCount == 0) viewModel.deleteCategory(category)
                            else showReplacementPicker = true
                        }, enabled = !isDeleting) {
                            if (isDeleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (ready.usage.transactionCount == 0)
                                StringRes.delete else StringRes.category_choose_replacement),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = closeDeleteDialog, enabled = !isDeleting) {
                        Text(stringResource(StringRes.cancel))
                    }
                }
            )
        }
    }

    // --- Dialog 2: chọn danh mục thay thế ---
    if (showReplacementPicker && categoryPendingDelete != null) {
        val categoryToDelete = categoryPendingDelete!!
        val options = viewModel.getReplacementOptions(categoryToDelete)
        val validSelection = selectedReplacement?.takeIf { selected ->
            options.any { it.userId == selected.userId && it.id == selected.id }
        }

        Dialog(onDismissRequest = closeDeleteDialog) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(enabled = !isDeleting, onClick = closeDeleteDialog) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(StringRes.cancel))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(StringRes.category_choose_replacement),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (options.isEmpty()) {
                        Text(
                            text = stringResource(StringRes.category_no_replacement),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(options, key = { it.id }) { option ->
                                val isSelected = selectedReplacement?.id == option.id
                                val optionColor = remember(option.colorHex) {
                                    runCatching { Color(option.colorHex.toColorInt()) }.getOrDefault(Color.Gray)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .combinedClickable(onClick = {
                                            if (!isDeleting) {
                                                selectedReplacement = option
                                                deleteFailure = null
                                            }
                                        })
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(10.dp).background(optionColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = option.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                    }
                    deleteFailure?.let { error ->
                        val messageRes = when (error) {
                            TransactionError.CategoryInUse -> StringRes.category_delete_in_use
                            TransactionError.CategoryReplacementRequired -> StringRes.category_delete_changed_need_replacement
                            TransactionError.CategoryNotSelected -> StringRes.category_delete_invalid
                            TransactionError.NotAuthenticated -> StringRes.error_not_authenticated
                            else -> StringRes.category_delete_failed
                        }
                        Text(stringResource(messageRes),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = closeDeleteDialog, enabled = !isDeleting) {
                            Text(stringResource(StringRes.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            validSelection?.let { viewModel.deleteCategory(categoryToDelete, it) }
                        }, enabled = validSelection != null && !isDeleting) {
                            if (isDeleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(StringRes.category_move_and_delete))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryGrid(
    categories: List<CategoryModel>,
    isManageMode: Boolean,
    onTap: (CategoryModel) -> Unit,
    onLongPress: (CategoryModel) -> Unit,
    onAddClick: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryItemView(
                category = category,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        // Chế độ quản lý: tap không làm gì. Chế độ chọn: tap chọn + trả về.
                        if (!isManageMode) onTap(category)
                    },
                    onLongClick = {
                        // Nhấn giữ chỉ có tác dụng ở chế độ quản lý.
                        if (isManageMode) onLongPress(category)
                    }
                )
            )
        }

        item(key = "add_tile") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(onClick = { onAddClick() })
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(StringRes.add_category_title),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(StringRes.create), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CategoryItemView(
    category: CategoryModel,
    modifier: Modifier = Modifier
) {
    val defaultCategoryColor = MaterialTheme.colorScheme.primary
    val categoryColor = remember(category.colorHex, defaultCategoryColor) {
        runCatching { Color(category.colorHex.toColorInt()) }.getOrDefault(defaultCategoryColor)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(categoryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberCategoryIcon(category.iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = category.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
