package com.example.moneymatev2.presentation.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.R
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.TransactionError
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: TransactionEntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == TransactionEntryEvent.Deleted) onDeleted() }
    }
    val tx = state.transaction
    val accent = if (tx?.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(MaterialTheme.colorScheme.primary).padding(horizontal = 24.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(StringRes.back),
                Modifier.size(28.dp).clickable(onClick = onBack), tint = Color.White)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.transaction_detail_title), color = Color.White,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (tx == null || state.ownerId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(StringRes.error_transaction_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    DetailRow(stringResource(StringRes.amount),
                        "${NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(tx.money.amountMinor)} đ", accent)
                    DetailRow(stringResource(R.string.transaction_category),
                        if (state.category?.isMissing == true || state.category == null)
                            stringResource(R.string.category_missing_reference) else state.category!!.name)
                    DetailRow(stringResource(R.string.transaction_type),
                        stringResource(if (tx.type == TransactionType.EXPENSE) StringRes.expense else StringRes.income))
                    DetailRow(stringResource(R.string.transaction_time),
                        remember(tx.createdAt) { SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale("vi", "VN")).format(Date(tx.createdAt)) })
                    DetailRow(stringResource(StringRes.note), tx.note?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.transaction_no_note))
                    if (state.error != null) Text(stringResource(R.string.transaction_action_failed),
                        color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(30.dp), enabled = !state.deleting) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.transaction_edit))
                    }
                    Button(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(30.dp), enabled = !state.deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(StringRes.delete))
                    }
                }
            }
        }
    }
    if (confirmDelete && tx != null) AlertDialog(
        onDismissRequest = { if (!state.deleting) confirmDelete = false },
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.transaction_delete_title)) },
        text = { Column {
            Text(stringResource(R.string.transaction_delete_message))
            if (state.error != null) Text(stringResource(R.string.transaction_action_failed),
                color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = {
            TextButton(onClick = viewModel::delete, enabled = !state.deleting) {
                if (state.deleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(StringRes.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !state.deleting) {
            Text(stringResource(StringRes.cancel))
        } }
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.62f), color = valueColor,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onManageCategoryClick: () -> Unit,
    viewModel: TransactionEntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == TransactionEntryEvent.Saved) onSaved() }
    }
    val themeColor = if (state.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        HeaderAdd(themeColor, state.type, viewModel::typeChanged, onBack,
            titleRes = R.string.transaction_edit_title)
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.transaction == null || state.ownerId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(StringRes.error_transaction_not_found))
            }
        } else {
            val currentCategory = state.category?.takeIf { it.id == state.selectedCategoryId && it.type == state.type }
            val gridCategories = if (currentCategory != null && state.categories.none { it.id == currentCategory.id })
                listOf(currentCategory) + state.categories else state.categories
            Column {
                if (state.error != null && state.error != "amount" && state.error != "category")
                    Text(stringResource(R.string.transaction_action_failed),
                        Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
                FormSection(
                    amountText = state.amountText, note = state.note, categories = gridCategories,
                    selectedCategoryId = state.selectedCategoryId, selectedDate = state.selectedDate,
                    isSaving = state.saving,
                    amountError = if (state.error == "amount" ||
                        (state.amountText.isNotBlank() && (state.amountText.toLongOrNull() ?: 0L) <= 0L))
                        TransactionError.InvalidAmount else null,
                    generalError = if (state.error == "category") TransactionError.CategoryNotSelected else null,
                    themeColor = themeColor, currency = "đ", onAmountChange = viewModel::amountChanged,
                    onNoteChange = viewModel::noteChanged, onCategorySelected = viewModel::categoryChanged,
                    onDateClicked = { showDatePicker = true }, onConfirm = viewModel::save,
                    onManageCategoryClick = onManageCategoryClick, confirmTextRes = R.string.transaction_save_update,
                    categoryWarning = if (currentCategory?.isArchived == true)
                        stringResource(R.string.transaction_archived_category)
                    else if (state.category == null || state.category?.isMissing == true)
                        stringResource(R.string.transaction_missing_category_warning)
                    else if (state.selectedCategoryId == null)
                        stringResource(StringRes.error_category_not_selected)
                    else null
                )
            }
        }
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.selectedDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let(viewModel::dateChanged); showDatePicker = false }) {
                Text(stringResource(StringRes.confirm))
            }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(StringRes.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
}
