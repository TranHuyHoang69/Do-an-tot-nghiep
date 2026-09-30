package com.example.moneymatev2.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.presentation.theme.AppTopBarColor
import com.example.moneymatev2.ui.item.CategoryIconMap
import com.example.moneymatev2.ui.item.rememberCategoryIcon
import kotlinx.coroutines.flow.receiveAsFlow


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryScreen(
    viewModel: AddCategoryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.formState.collectAsState()
    val groupedIcons = remember { CategoryIconMap.groupedKeys() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.events.receiveAsFlow().collect { event ->
            when (event) {
                is AddCategoryEvent.SaveSuccessfully -> onSaved()
                is AddCategoryEvent.SaveFailed -> errorMessage = "Không thể lưu danh mục"
            }
        }
    }

    val themeColor = if (state.type == TransactionType.EXPENSE)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(StringRes.add_category_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTopBarColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item(span = { GridItemSpan(6) }, key = "input_field") {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.onNameChange(it) },
                        label = { Text(text = stringResource(StringRes.category_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = themeColor,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                item(span = { GridItemSpan(6) }, key = "type_radio_buttons") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = state.type == TransactionType.EXPENSE,
                            onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) },
                            colors = RadioButtonDefaults.colors(selectedColor = themeColor)
                        )
                        Text(text = stringResource(StringRes.expense), fontWeight = FontWeight.Medium, fontSize = 14.sp)

                        Spacer(modifier = Modifier.width(24.dp))

                        RadioButton(
                            selected = state.type == TransactionType.INCOME,
                            onClick = { viewModel.onTypeChange(TransactionType.INCOME) },
                            colors = RadioButtonDefaults.colors(selectedColor = themeColor)
                        )
                        Text(text = stringResource(StringRes.income), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }

                item(span = { GridItemSpan(6) }, key = "color_title") {
                    Text(
                        text = stringResource(StringRes.category_color),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(items = viewModel.availableColors, key = { it }) { colorHex ->
                    val composeColor = remember(colorHex) {
                        runCatching { Color(colorHex.toColorInt()) }.getOrDefault(Color.Gray)
                    }
                    val isSelected = state.selectedColorHex == colorHex
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(composeColor)
                            .clickable { viewModel.onColorChange(colorHex) }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }

                item(span = { GridItemSpan(6) }, key = "icon_title") {
                    Text(
                        text = stringResource(StringRes.category_icon),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                    )
                }

                groupedIcons.forEach { (groupName, iconsInGroup) ->
                    item(span = { GridItemSpan(6) }, key = "header_group_$groupName") {
                        Text(
                            text = groupName,
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(items = iconsInGroup, key = { "icon_${groupName}_$it" }) { iconKey ->
                        IconItem(
                            iconKey = iconKey,
                            isSelected = state.selectedIconKey == iconKey,
                            themeColor = themeColor,
                            onClick = { viewModel.onIconChange(iconKey) }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    enabled = state.name.isNotBlank() && !state.isSaving
                ) {
                    Text(
                        text = stringResource(StringRes.save_category),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun IconItem(
    iconKey: String,
    isSelected: Boolean,
    themeColor: Color,
    onClick: () -> Unit
) {
    val painter = rememberCategoryIcon(iconKey)
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) themeColor else themeColor.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = iconKey,
            tint = if (isSelected) Color.White else themeColor,
            modifier = Modifier.size(22.dp)
        )
    }
}