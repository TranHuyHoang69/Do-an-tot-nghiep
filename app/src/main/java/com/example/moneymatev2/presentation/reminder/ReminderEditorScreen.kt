package com.example.moneymatev2.presentation.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.RepeatRule
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(
    formState: ReminderFormState,
    viewModel: ReminderViewModel,
    snackbarHostState: SnackbarHostState,
    onBackClick : () -> Unit
) {
    var repeatExpanded by remember {
        mutableStateOf(false)
    }

    var showDatePicker by remember {
        mutableStateOf(false)
    }

    var showTimePicker by remember {
        mutableStateOf(false)
    }

    val repeatOptions =
        remember {
            listOf(
                RepeatRule.NONE,
                RepeatRule.DAILY,
                RepeatRule.WEEKLY,
                RepeatRule.MONTHLY
            )
        }

    val dateDisplayString =
        remember(
            formState.selectedDateMillis
        ) {
            DateFormat
                .getDateInstance(
                    DateFormat.MEDIUM
                )
                .format(
                    Date(
                        formState
                            .selectedDateMillis
                    )
                )
        }

    val timeDisplayString =
        remember(
            formState.hour,
            formState.minute
        ) {
            String.format(
                "%02d:%02d",
                formState.hour,
                formState.minute
            )
        }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    formState
                        .selectedDateMillis
            )

        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis =
                            datePickerState
                                .selectedDateMillis

                        if (
                            selectedMillis != null
                        ) {
                            /*
                             * Material DatePicker trả ngày
                             * theo UTC. Chuyển thành ngày
                             * local trước khi đưa cho VM để
                             * tránh lệch ngày theo timezone.
                             */
                            val utcCalendar =
                                Calendar
                                    .getInstance(
                                        java.util
                                            .TimeZone
                                            .getTimeZone(
                                                "UTC"
                                            )
                                    )
                                    .apply {
                                        timeInMillis =
                                            selectedMillis
                                    }

                            val localCalendar =
                                Calendar
                                    .getInstance()
                                    .apply {
                                        set(
                                            utcCalendar.get(
                                                Calendar.YEAR
                                            ),
                                            utcCalendar.get(
                                                Calendar.MONTH
                                            ),
                                            utcCalendar.get(
                                                Calendar.DAY_OF_MONTH
                                            )
                                        )
                                    }

                            viewModel.onDateChange(
                                localCalendar
                                    .timeInMillis
                            )
                        }

                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = stringResource(
                            StringRes.confirm
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = stringResource(
                            StringRes.cancel
                        )
                    )
                }
            }
        ) {
            DatePicker(
                state = datePickerState
            )
        }
    }

    if (showTimePicker) {
        val timePickerState =
            rememberTimePickerState(
                initialHour =
                    formState.hour,
                initialMinute =
                    formState.minute,
                is24Hour = true
            )

        Dialog(
            onDismissRequest = {
                showTimePicker = false
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape =
                    RoundedCornerShape(
                        20.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceContainerHigh
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(20.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(
                            StringRes.set_reminder_time
                        ),
                        fontSize = 18.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(20.dp)
                    )

                    TimePicker(
                        state =
                            timePickerState
                    )

                    Spacer(
                        modifier =
                            Modifier.height(20.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showTimePicker =
                                    false
                            }
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        StringRes.cancel
                                    )
                            )
                        }

                        TextButton(
                            onClick = {
                                viewModel
                                    .onTimeChange(
                                        hour =
                                            timePickerState
                                                .hour,
                                        minute =
                                            timePickerState
                                                .minute
                                    )

                                showTimePicker =
                                    false
                            }
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        StringRes.confirm
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        containerColor =
            MaterialTheme.colorScheme.background,

        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorHeader(
                isEditing = formState.isEditing,
                onBackClick = onBackClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        24.dp
                    )
            ) {
                UnderlinedInputField(
                    label =
                        stringResource(
                            StringRes.reminder_name
                        ),
                    value =
                        formState.title,
                    placeholder =
                        stringResource(
                            StringRes.reminder_name_hint
                        ),
                    singleLine = true,
                    onValueChange =
                        viewModel::onTitleChange
                )

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            StringRes.repeat_type
                        ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 16.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        repeatExpanded =
                                            true
                                    }
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement
                                        .SpaceBetween,
                                verticalAlignment =
                                    Alignment
                                        .CenterVertically
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            repeatLabelRes(
                                                formState
                                                    .repeatRule
                                            )
                                        ),
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    fontSize =
                                        18.sp
                                )

                                Icon(
                                    imageVector =
                                        Icons.Default
                                            .KeyboardArrowDown,
                                    contentDescription =
                                        null
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant
                                                .copy(
                                                    alpha =
                                                        0.4f
                                                )
                                        )
                            )
                        }

                        DropdownMenu(
                            expanded =
                                repeatExpanded,
                            onDismissRequest = {
                                repeatExpanded =
                                    false
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(
                                        0.75f
                                    )
                        ) {
                            repeatOptions
                                .forEach { repeatRule ->

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text =
                                                    stringResource(
                                                        repeatLabelRes(
                                                            repeatRule
                                                        )
                                                    ),
                                                color =
                                                    if (
                                                        repeatRule ==
                                                        formState.repeatRule
                                                    ) {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                    } else {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurface
                                                    }
                                            )
                                        },
                                        onClick = {
                                            viewModel
                                                .onRepeatChange(
                                                    repeatRule
                                                )

                                            repeatExpanded =
                                                false
                                        }
                                    )
                                }
                        }
                    }
                }

                SelectionField(
                    label =
                        stringResource(
                            StringRes.reminder_date
                        ),
                    value =
                        dateDisplayString,
                    onClick = {
                        showDatePicker = true
                    }
                )

                SelectionField(
                    label =
                        stringResource(
                            StringRes.reminder_time
                        ),
                    value =
                        timeDisplayString,
                    onClick = {
                        showTimePicker = true
                    }
                )

                UnderlinedInputField(
                    label =
                        stringResource(
                            StringRes.reminder_note
                        ),
                    value =
                        formState.note,
                    placeholder =
                        stringResource(
                            StringRes.reminder_note_hint
                        ),
                    singleLine = false,
                    onValueChange =
                        viewModel::onNoteChange
                )
            }

            Button(
                onClick = {
                    viewModel.saveReminder()
                },
                enabled =
                    formState.title
                        .isNotBlank() &&
                            !formState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp
                    )
                    .height(56.dp),
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimary
                        )
            ) {
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onPrimary
                    )
                } else {
                    Text(
                        text =
                            if (
                                formState.isEditing
                            ) {
                                stringResource(
                                    StringRes.save
                                )
                            } else {
                                stringResource(
                                    StringRes.add_reminder
                                )
                            },
                        fontSize = 18.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorHeader(
    isEditing: Boolean,
    onBackClick : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer,
                shape =
                    RoundedCornerShape(
                        bottomStart = 32.dp,
                        bottomEnd = 32.dp
                    )
            )
            .padding(
                horizontal = 16.dp,
                vertical = 20.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick
            ) {
                Icon(
                    imageVector =
                        Icons.AutoMirrored
                            .Filled
                            .ArrowBack,
                    contentDescription =
                        stringResource(
                            StringRes.back
                        ),
                    tint =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer
                )
            }

            Spacer(
                modifier =
                    Modifier.size(12.dp)
            )

            Text(
                text =
                    if (isEditing) {
                        stringResource(
                            StringRes.edit_reminder
                        )
                    } else {
                        stringResource(
                            StringRes.add_reminder
                        )
                    },
                color =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UnderlinedInputField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize = 16.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        BasicTextField(
            value = value,
            onValueChange =
                onValueChange,
            singleLine =
                singleLine,
            textStyle =
                TextStyle(
                    color =
                        MaterialTheme
                            .colorScheme
                            .onBackground,
                    fontSize = 18.sp
                ),
            cursorBrush =
                SolidColor(
                    MaterialTheme
                        .colorScheme
                        .primary
                ),
            modifier =
                Modifier.fillMaxWidth(),
            decorationBox = {
                    innerTextField ->

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = 28.dp
                                )
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text =
                                    placeholder,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                                        .copy(
                                            alpha =
                                                0.6f
                                        ),
                                fontSize =
                                    18.sp
                            )
                        }

                        /*
                         * Luôn phải gọi innerTextField,
                         * kể cả khi value rỗng.
                         */
                        innerTextField()
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    if (
                                        value.isNotEmpty()
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                            .copy(
                                                alpha =
                                                    0.4f
                                            )
                                    }
                                )
                    )
                }
            }
        )
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            fontSize = 16.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text = value,
            color =
                MaterialTheme
                    .colorScheme
                    .primary,
            fontSize = 18.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                        .copy(
                            alpha = 0.4f
                        )
                )
        )
    }
}

private fun repeatLabelRes(
    repeatRule: RepeatRule
): Int {
    return when (repeatRule) {
        RepeatRule.NONE ->
            StringRes.repeat_none

        RepeatRule.DAILY ->
            StringRes.repeat_daily

        RepeatRule.WEEKLY ->
            StringRes.repeat_weekly

        RepeatRule.MONTHLY ->
            StringRes.repeat_monthly

        else ->
            StringRes.repeat_none
    }
}