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
import com.example.moneymatev2.ui.item.rememberCategoryIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManagementCategoryScreen(
    viewModel: CategoryManagementViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onAddCategoryClick: (TransactionType) -> Unit,
    onCategoryClick: ((CategoryModel) -> Unit)? = null // null = chế độ quản lý (long-press xóa), non-null = chế độ chọn
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val isManageMode = onCategoryClick == null

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = remember { listOf(StringRes.expense, StringRes.income) }

    // --- State cho luồng xóa (chỉ dùng khi isManageMode) ---
    var categoryPendingDelete by remember { mutableStateOf<CategoryModel?>(null) } // đang hiện dialog cảnh báo
    var showReplacementPicker by remember { mutableStateOf(false) } // đang hiện dialog chọn category thay thế
    var selectedReplacement by remember { mutableStateOf<CategoryModel?>(null) }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { event ->
            when (event) {
                is CategoryManagementViewModel.DeleteCategoryEvent.Success -> {
                    showReplacementPicker = false
                    categoryPendingDelete = null
                    selectedReplacement = null
                }
                is CategoryManagementViewModel.DeleteCategoryEvent.Failed -> {
                    // TODO: hiện Snackbar báo lỗi nếu cần, hiện tại chỉ đóng dialog
                    showReplacementPicker = false
                    categoryPendingDelete = null
                    selectedReplacement = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(56.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                    onLongPress = { category -> categoryPendingDelete = category },
                    onAddClick = { onAddCategoryClick(currentType) }
                )
            }
        }
    }

    // --- Dialog 1: cảnh báo trước khi xóa ---
    categoryPendingDelete?.let { category ->
        if (!showReplacementPicker) {
            AlertDialog(
                onDismissRequest = { categoryPendingDelete = null },
                title = { Text("Xóa danh mục") },
                text = {
                    Text("Vui lòng chọn danh mục thay thế cho các giao dịch thuộc danh mục sắp xóa")
                },
                confirmButton = {
                    TextButton(onClick = { showReplacementPicker = true }) {
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryPendingDelete = null }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }

    // --- Dialog 2: chọn danh mục thay thế ---
    if (showReplacementPicker && categoryPendingDelete != null) {
        val categoryToDelete = categoryPendingDelete!!
        val options = remember(categoryToDelete) { viewModel.getReplacementOptions(categoryToDelete) }

        Dialog(onDismissRequest = {
            showReplacementPicker = false
            categoryPendingDelete = null
            selectedReplacement = null
        }) {
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
                        IconButton(onClick = {
                            showReplacementPicker = false
                            categoryPendingDelete = null
                            selectedReplacement = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Hủy")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Chọn danh mục",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (options.isEmpty()) {
                        Text(
                            text = "Không có danh mục khác cùng loại để thay thế. Hãy tạo thêm danh mục trước.",
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
                                        .combinedClickable(onClick = { selectedReplacement = option })
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

                        // Nút xác nhận chỉ hiện khi đã chọn xong -- không chỉ disable, mà ẨN hẳn.
                        if (selectedReplacement != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.deleteCategory(categoryToDelete.id, selectedReplacement!!.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Xác nhận")
                            }
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