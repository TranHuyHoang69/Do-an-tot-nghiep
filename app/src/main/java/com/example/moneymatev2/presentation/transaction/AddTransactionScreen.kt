package com.example.moneymatev2.presentation.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.presentation.theme.StringResource
import com.example.moneymatev2.core.util.toUiMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: AddTransactionViewmodel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: () -> Unit
){

    val state by viewModel.formState.collectAsState()
    var errorMessage by remember { mutableStateOf<TransactionError?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when(event){
                is AddTransactionEvent.SaveSuccessfully -> {
                    errorMessage = null
                    onSaved()
                }
                is AddTransactionEvent.SaveFailed -> {
                    errorMessage = event.error
                }
            }
        }
    }

    val themeColor = if(state.type == TransactionType.EXPENSE){
        MaterialTheme.colorScheme.error
    }else{
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HeaderAdd(
            themeColor = themeColor,
            selectedType = state.type,
            onTabSelected = {viewModel.onTypeChange(it)},
            onBack = onBack
        )

        FormSection(
            amountText = state.amountText,
            note = state.note,
            categories = state.categories,
            selectedCategoryId = state.selectedCategoryId,
            selectedDate = state.selectedDate,
            isSaving = state.isSaving,
            amountError = if (errorMessage == TransactionError.InvalidAmount) errorMessage else null,
            generalError = errorMessage?.takeIf { it != TransactionError.InvalidAmount },
            themeColor = themeColor,
            currency = "đ",
            onAmountChange = { errorMessage = null; viewModel.onAmountChange(it) },
            onNoteChange = { viewModel.onNoteChange(it) },
            onCategorySelected = { errorMessage = null; viewModel.onCategorySelected(it.id) },
            onDateClicked = { showDatePicker = true },
            onConfirm = { viewModel.save() }

        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateChange(it) }
                    showDatePicker = false
                }) { Text(StringResource(StringRes.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(StringResource(StringRes.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun HeaderAdd(
    themeColor: Color,
    selectedType: TransactionType,
    onTabSelected: (TransactionType) -> Unit,
    onBack: () -> Unit
){
    val expenseText = stringResource(StringRes.expense).uppercase()
    val incomeText = stringResource(StringRes.income).uppercase()
    val options = remember(expenseText, incomeText){
        listOf(
            TransactionType.EXPENSE to expenseText,
            TransactionType.INCOME to incomeText
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(themeColor)
            .padding(top = 40.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = StringResource(StringRes.back),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = StringResource(StringRes.add_transaction_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(0.15f))
                .padding(4.dp)
        ) {
            options.forEach { (type, tabTitle) ->
                val isSelected = selectedType == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color.White.copy(0.25f) else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clickable { onTabSelected(type) }
                ){
                    Text(
                        text = tabTitle,
                        color = if(isSelected) Color.White else Color.White.copy(0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FormSection(
    amountText: String,
    note: String,
    categories: List<CategoryModel>,
    selectedCategoryId: String?,
    selectedDate: Long,
    isSaving: Boolean,
    amountError: TransactionError?,
    generalError: TransactionError?,
    themeColor: Color,
    currency: String,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategorySelected: (CategoryModel) -> Unit,
    onDateClicked: () -> Unit,
    onConfirm: () -> Unit
){

    val canConfirm = amountText.isNotBlank() && amountText.toLongOrNull()?.let { it > 0 } == true
            && selectedCategoryId != null && !isSaving

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item(key = "amount_input") {
            Text(
                text = StringResource(StringRes.amount),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            AmountInputField(
                amountMinor = amountText,
                themeColor = themeColor,
                currency = currency,
                error = amountError,
                onAmountChange = onAmountChange
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        item(key = "category_section") {
            Text(
                text = StringResource(StringRes.category_management),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            //Success -> CategoryGrid và Loading -> CircularProgress và Error
            CategoryGrid(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                themeColor = themeColor,
                onCategorySelected = onCategorySelected
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        item(key = ("additional_details")) {
            TransactionDetailsCard(
                selectedDate = selectedDate,
                note = note,
                themeColor = themeColor,
                onDateClicked = onDateClicked,
                onNoteChanged = onNoteChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                enabled = canConfirm
            ) {
                Text(
                    text = StringResource(StringRes.confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun TransactionDetailsCard(
    selectedDate: Long,
    note: String,
    themeColor: Color,
    onDateClicked: () -> Unit,
    onNoteChanged: (String) -> Unit
) {

    val dateLabel = StringResource(StringRes.selected_date_hint)
    val errorLabel = StringResource(StringRes.error_format)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable{ onDateClicked() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ){
                Column {
                    Text(
                        text = StringResource(StringRes.transaction_date),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = formatLongToDate(selectedDate, dateLabel, errorLabel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(Icons.Default.DateRange, null, tint = themeColor)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.15f)
            )

            Text(
                text = StringResource(StringRes.note),
                fontSize = 14.sp,
                color = Color.Gray
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                placeholder = {Text(StringResource(StringRes.note_placeholder))},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun CategoryGrid(
    categories: List<CategoryModel>,
    selectedCategoryId: String?,
    themeColor: Color,
    onCategorySelected: (CategoryModel) -> Unit
) {
    // TODO: khi có màn "Xem tất cả danh mục" riêng, giới hạn hiển thị ở đây (vd take(8) + ô "Thêm")
    // Hiện tại hiển thị toàn bộ để tránh phát sinh phạm vi ngoài yêu cầu ban đầu
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.height(((categories.size / 4 + 1) * 90).dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryItem(
                category = category,
                themeColor = themeColor,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: CategoryModel,
    themeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val resId = remember(category.iconKey) {
        context.resources.getIdentifier(category.iconKey,"drawable",context.packageName)
    }

    val categoryColor = remember(category.colorHex) {
        try{
            Color(category.colorHex.toColorInt())
        }catch (e: Exception){
            Color.Transparent
        }.let { parsedColor ->
            if(parsedColor == Color.Transparent) themeColor else parsedColor
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp).clickable { onClick() }
            .background(
                if (isSelected) categoryColor.copy(0.15f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
                )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) categoryColor.copy(0.5f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(8.dp, 6.dp, 4.dp, 4.dp)
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(if(isSelected) categoryColor else categoryColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ){
            if(resId != 0) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = category.name,
                    tint = if(isSelected) Color.White else categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }else{
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = if(isSelected) Color.White else categoryColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = category.name,
            fontSize = 12.sp,
            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = if(isSelected) categoryColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun AmountInputField(
    amountMinor: String,
    themeColor: Color,
    currency: String,
    error: TransactionError?,
    onAmountChange: (String) -> Unit
) {
    OutlinedTextField(
        value = amountMinor,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = themeColor),
        placeholder = {Text("0", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))},
        trailingIcon = {Text(currency, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = themeColor) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = error != null,
        supportingText = error?.let {
            { Text(text = it.toUiMessage(), color = MaterialTheme.colorScheme.error) }
        },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor =themeColor),
        visualTransformation = VisualTransformation { thousandSeparatorTransformation(it) }
    )
}

fun thousandSeparatorTransformation(text: AnnotatedString): TransformedText {
    val originalText = text.text
    if(originalText.isEmpty()){
        return TransformedText(text, OffsetMapping.Identity)
    }

    val formattedText = StringBuilder()
    var count = 0
    for(i in originalText.indices.reversed()){
        formattedText.append(originalText[i])
        count++
        if(count % 3 == 0 && i != 0){
            formattedText.append(".")
        }
    }
    val out = formattedText.reversed().toString()

    val offsetMapping = object : OffsetMapping{
        override fun originalToTransformed(offset: Int): Int {
            if(offset <= 0) return offset
            var dots = 0
            val length = originalText.length
            for (i in 0 until offset) {
                val revIdx = length - 1 - i
                if((length - revIdx) % 3 == 0 && revIdx != 0){
                    dots++
                }
            }
            val realDots = if(offset == length && length % 3 == 0)dots - 1 else dots
            val totalOffset = offset + (out.length - originalText.length) - (dots - realDots)
            return totalOffset.coerceIn(0, out.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var originalOffset = offset
            for(i in 0 until offset){
                if(i < out.length && out[i] == '.'){
                    originalOffset--
                }
            }
            return originalOffset.coerceIn(0, originalText.length)
        }
    }
    return TransformedText(AnnotatedString(out), offsetMapping)
}

fun formatLongToDate(timestamp: Long, emptyHint: String, errorHint: String): String {
    if (timestamp == 0L) return emptyHint
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    return try{
        dateFormat.format(Date(timestamp))
    }catch (e: Exception){
        errorHint
    }
}