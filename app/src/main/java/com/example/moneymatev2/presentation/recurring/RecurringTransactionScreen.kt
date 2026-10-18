package com.example.moneymatev2.presentation.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.RecurringFrequency
import com.example.moneymatev2.domain.model.RecurringTransactionModel
import com.example.moneymatev2.domain.usecase.recurring.RecurringScheduleCalculator
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

@Composable
fun RecurringTransactionScreen(
    onOpenDrawer: () -> Unit,
    viewModel: RecurringTransactionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ownerId = state.userId
    val saving by viewModel.isSaving.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editorKey by remember(state.userId) { mutableStateOf<String?>(null) } // null=list; ""=create; id=edit
    var pendingDelete by remember(state.userId) { mutableStateOf<RecurringTransactionModel?>(null) }

    val savedMessage = stringResource(R.string.recurring_saved)
    val deletedMessage = stringResource(R.string.recurring_deleted)
    LaunchedEffect(viewModel, savedMessage, deletedMessage) {
        viewModel.events.collect { event ->
            when (event) {
                RecurringEvent.Saved -> {
                    editorKey = null
                    scope.launch { snackbar.showSnackbar(savedMessage) }
                }
                RecurringEvent.Deleted -> scope.launch { snackbar.showSnackbar(deletedMessage) }
                is RecurringEvent.Error -> scope.launch { snackbar.showSnackbar(event.message) }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.delete(item)
                }) { Text(stringResource(R.string.recurring_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.recurring_cancel)) }
            }
        )
    }

    val editingItem = state.templates.firstOrNull { it.id == editorKey }
    if (editorKey != null) {
        if (editorKey!!.isNotEmpty() && editingItem == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator()
                else Text(stringResource(R.string.recurring_unknown_category))
            }
            return
        }
        RecurringEditor(
            existing = editingItem,
            snackbar = snackbar,
            categories = state.categories.filter { !it.isArchived },
            saving = saving,
            onBack = { editorKey = null },
            onSave = { type, amount, categoryId, note, frequency, date, zoneId, active ->
                viewModel.save(ownerId, editingItem, type, amount, categoryId, note, frequency, date, zoneId, active)
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.signedIn) {
                FloatingActionButton(
                    onClick = { editorKey = "" },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(64.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.recurring_add), modifier = Modifier.size(30.dp))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RecurringHeader(
                title = stringResource(R.string.recurring_title),
                onBackOrMenu = onOpenDrawer,
                editor = false
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !state.signedIn -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.recurring_login_required))
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                state.templates.isEmpty() -> RecurringEmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.templates, key = { it.id }) { item ->
                        RecurringCard(
                            item = item,
                            categoryName = state.categories.firstOrNull { it.id == item.categoryId }?.name ?: stringResource(R.string.recurring_unknown_category),
                            onEdit = { editorKey = item.id },
                            onDelete = { pendingDelete = item },
                            onToggle = { viewModel.toggle(item, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringHeader(title: String, onBackOrMenu: () -> Unit, editor: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackOrMenu) {
            Icon(
                imageVector = if (editor) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(title, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecurringEmptyState() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.recurring_empty), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.recurring_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecurringCard(
    item: RecurringTransactionModel,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val typeColor = if (item.type == TransactionType.INCOME) Color(0xFF168A4A) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(categoryName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${frequencyName(item.frequency)} • ${stringResource(R.string.recurring_next_run)} ${formatDate(item.nextRunAt, item.timeZoneId)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (item.isActive) stringResource(R.string.recurring_on) else stringResource(R.string.recurring_off), fontSize = 12.sp)
                    Switch(checked = item.isActive, onCheckedChange = onToggle)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (item.type == TransactionType.INCOME) "+" else "−"}${moneyFormat(item.amountMinor)} ₫",
                    color = typeColor, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.recurring_edit))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.recurring_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            item.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun frequencyName(value: RecurringFrequency): String = stringResource(when (value) {
    RecurringFrequency.DAILY -> R.string.recurring_daily
    RecurringFrequency.WEEKLY -> R.string.recurring_weekly
    RecurringFrequency.EVERY_TWO_WEEKS -> R.string.recurring_2weeks
    RecurringFrequency.EVERY_FOUR_WEEKS -> R.string.recurring_4weeks
    RecurringFrequency.MONTHLY -> R.string.recurring_monthly
    RecurringFrequency.EVERY_TWO_MONTHS -> R.string.recurring_2months
    RecurringFrequency.QUARTERLY -> R.string.recurring_quarterly
    RecurringFrequency.EVERY_SIX_MONTHS -> R.string.recurring_6months
    RecurringFrequency.YEARLY -> R.string.recurring_yearly
})

private fun moneyFormat(value: Long): String = NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(value)
private fun formatDate(epoch: Long, zoneId: String): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditor(
    existing: RecurringTransactionModel?,
    snackbar: SnackbarHostState,
    categories: List<CategoryModel>,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (TransactionType, String, String, String, RecurringFrequency, Long, String, Boolean) -> Unit
) {
    val zoneId = remember(existing?.id) { existing?.timeZoneId ?: TimeZone.getDefault().id }
    val calculator = remember { RecurringScheduleCalculator() }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amountMinor?.toString().orEmpty()) }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var frequency by remember(existing?.id) { mutableStateOf(existing?.frequency ?: RecurringFrequency.MONTHLY) }
    var date by remember(existing?.id) {
        mutableLongStateOf(existing?.startAt ?: LocalDate.now(ZoneId.of(zoneId)).atStartOfDay(ZoneId.of(zoneId)).toInstant().toEpochMilli())
    }
    var categoryId by remember(existing?.id) { mutableStateOf(existing?.categoryId.orEmpty()) }
    var active by remember(existing?.id) { mutableStateOf(existing?.isActive ?: true) }
    var typeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val validCategories = categories.filter { it.type == type }

    if (datePickerOpen) {
        val selectedUtc = remember(date) {
            Instant.ofEpochMilli(date).atZone(ZoneId.of(zoneId)).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedUtc)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { date = calculator.datePickerMillisToLocalStart(it, zoneId) }
                    datePickerOpen = false
                }) { Text(stringResource(R.string.recurring_select)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text(stringResource(R.string.recurring_cancel)) }
            }
        ) { DatePicker(state = picker) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RecurringHeader(
                title = if (existing == null) stringResource(R.string.recurring_add) else stringResource(R.string.recurring_edit_title),
                onBackOrMenu = onBack, editor = true
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecurringSelector(
                    label = stringResource(R.string.recurring_type),
                    value = if (type == TransactionType.EXPENSE) stringResource(R.string.recurring_expense) else stringResource(R.string.recurring_income),
                    expanded = typeExpanded, toggle = { typeExpanded = it }
                ) {
                    TransactionType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == TransactionType.EXPENSE) stringResource(R.string.recurring_expense) else stringResource(R.string.recurring_income)) },
                            onClick = { type = option; categoryId = ""; typeExpanded = false }
                        )
                    }
                }
                RecurringSelector(
                    label = stringResource(R.string.recurring_category),
                    value = validCategories.firstOrNull { it.id == categoryId }?.name ?: stringResource(R.string.recurring_choose_category),
                    expanded = categoryExpanded, toggle = { categoryExpanded = it }
                ) {
                    validCategories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; categoryExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter(Char::isDigit).take(18) },
                    label = { Text(stringResource(R.string.recurring_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.recurring_note)) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                RecurringSelector(
                    label = stringResource(R.string.recurring_frequency), value = frequencyName(frequency),
                    expanded = frequencyExpanded, toggle = { frequencyExpanded = it }
                ) {
                    RecurringFrequency.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(frequencyName(option)) }, onClick = { frequency = option; frequencyExpanded = false })
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = formatDate(date, zoneId), onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.recurring_start_date)) }, modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { datePickerOpen = true })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recurring_active))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onSave(type, amount, categoryId, note, frequency, date, zoneId, active) },
                    enabled = !saving && amount.toLongOrNull()?.let { it > 0L } == true && categoryId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.recurring_save), fontSize = 18.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringSelector(
    label: String, value: String, expanded: Boolean, toggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { toggle(!expanded) }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { toggle(false) }, content = content)
    }
}
