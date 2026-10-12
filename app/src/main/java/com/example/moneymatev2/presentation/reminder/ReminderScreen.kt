package com.example.moneymatev2.presentation.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ReminderScreen(
    onOpenDrawer: () -> Unit,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEditor by rememberSaveable {
        mutableStateOf(false)
    }

    var showExactAlarmDialog by remember {
        mutableStateOf(false)
    }

    var reminderToDelete by remember {
        mutableStateOf<ReminderModel?>(null)
    }

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    /*
     * ---------------------------------------------------------
     * Strings
     * ---------------------------------------------------------
     */

    val savedMessage =
        stringResource(
            StringRes.reminder_saved
        )

    val deletedMessage =
        stringResource(
            StringRes.reminder_deleted
        )

    val invalidTitleMessage =
        stringResource(
            StringRes.reminder_invalid_title
        )

    val invalidTimeMessage =
        stringResource(
            StringRes.reminder_invalid_time
        )

    val loginRequiredMessage =
        stringResource(
            StringRes.reminder_login_required
        )

    val expiredMessage =
        stringResource(
            StringRes.reminder_expired
        )

    val notificationPermissionDeniedMessage =
        stringResource(
            StringRes.reminder_notification_permission_denied
        )

    /*
     * ---------------------------------------------------------
     * Exact alarm permission
     * ---------------------------------------------------------
     */

    val canScheduleExactAlarm: () -> Boolean = {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            true
        } else {
            val alarmManager =
                context.getSystemService(
                    Context.ALARM_SERVICE
                ) as AlarmManager

            alarmManager
                .canScheduleExactAlarms()
        }
    }

    /*
     * ---------------------------------------------------------
     * Notification permission
     * ---------------------------------------------------------
     */

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (!isGranted) {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            notificationPermissionDeniedMessage
                        )
                }
            }

            /*
             * Không chặn tạo reminder nếu user
             * không cấp quyền notification.
             */
            viewModel.startCreate()
            showEditor = true
        }

    /*
     * ---------------------------------------------------------
     * Mở form Create
     * ---------------------------------------------------------
     */

    val openCreateReminder: () -> Unit = {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            viewModel.startCreate()
            showEditor = true
        } else {

            val hasNotificationPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (hasNotificationPermission) {
                viewModel.startCreate()
                showEditor = true
            } else {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * Launcher mở màn Special Access:
     * "Alarms & reminders"
     * ---------------------------------------------------------
     */

    val exactAlarmSettingsLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.StartActivityForResult()
        ) {
            /*
             * Khi quay lại từ Settings:
             *
             * Nếu user vừa cấp quyền exact alarm
             * thì mở luôn form tạo Reminder.
             */
            if (canScheduleExactAlarm()) {
                openCreateReminder()
            }
        }

    val requestExactAlarmPermission: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            val intent =
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                ).apply {
                    data =
                        Uri.parse(
                            "package:${context.packageName}"
                        )
                }

            exactAlarmSettingsLauncher.launch(
                intent
            )
        } else {
            openCreateReminder()
        }
    }

    /*
     * ---------------------------------------------------------
     * One-shot events từ ViewModel
     * ---------------------------------------------------------
     */

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->

            when (event) {

                ReminderEvent.Saved -> {

                    // Đóng Editor ngay lập tức
                    showEditor = false

                    // Không block collector event
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            savedMessage
                        )
                    }
                }

                ReminderEvent.Deleted -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            deletedMessage
                        )
                    }
                }

                ReminderEvent.InvalidTitle -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            invalidTitleMessage
                        )
                    }
                }

                ReminderEvent.InvalidTime -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            invalidTimeMessage
                        )
                    }
                }

                ReminderEvent.NotAuthenticated -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            loginRequiredMessage
                        )
                    }
                }

                is ReminderEvent.ExpiredOneTimeReminder -> {
                    viewModel.startEdit(
                        event.reminderId
                    )

                    showEditor = true

                    scope.launch {
                        snackbarHostState.showSnackbar(
                            expiredMessage
                        )
                    }
                }

                is ReminderEvent.Error -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            event.message
                        )
                    }
                }
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * Exact alarm explanation dialog
     * ---------------------------------------------------------
     */

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = {
                showExactAlarmDialog = false
            },
            title = {
                Text(
                    text =
                        stringResource(
                            StringRes.reminder_exact_alarm_title
                        )
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            StringRes.reminder_exact_alarm_message
                        )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmDialog =
                            false

                        requestExactAlarmPermission()
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                StringRes.reminder_open_settings
                            )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExactAlarmDialog =
                            false

                        /*
                         * User vẫn có thể tạo reminder.
                         *
                         * AlarmReminderScheduler sẽ
                         * fallback sang inexact alarm.
                         */
                        openCreateReminder()
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                StringRes.reminder_later
                            )
                    )
                }
            }
        )
    }

    /*
     * ---------------------------------------------------------
     * Editor
     * ---------------------------------------------------------
     */

    if (showEditor) {
        ReminderEditorScreen(
            formState = formState,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBackClick = {
                showEditor = false
                viewModel.startCreate()
            }
        )

        return
    }

    /*
     * ---------------------------------------------------------
     * Reminder list
     * ---------------------------------------------------------
     */

    Scaffold(
        modifier =
            Modifier.fillMaxSize(),

        containerColor =
            MaterialTheme
                .colorScheme
                .background,

        snackbarHost = {
            SnackbarHost(
                hostState =
                    snackbarHostState
            )
        },

        floatingActionButton = {

            if (
                uiState !is
                        ReminderUiState.NotAuthenticated
            ) {
                FloatingActionButton(
                    onClick = {

                        /*
                         * Android < 12:
                         * không cần special exact alarm access.
                         *
                         * Android >= 12:
                         * kiểm tra quyền trước.
                         */
                        if (
                            canScheduleExactAlarm()
                        ) {
                            openCreateReminder()
                        } else {
                            showExactAlarmDialog =
                                true
                        }
                    },

                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer,

                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer,

                    shape =
                        CircleShape
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Add,

                        contentDescription =
                            stringResource(
                                StringRes.add_reminder
                            )
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    innerPadding
                )
        ) {

            ReminderHeader(
                onOpenDrawer =
                    onOpenDrawer
            )

            when (
                val state = uiState
            ) {

                ReminderUiState.Loading -> {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),

                        contentAlignment =
                            Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                ReminderUiState.NotAuthenticated -> {

                    ReminderLoginRequired()
                }

                is ReminderUiState.Error -> {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),

                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text =
                                state.message,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }
                }

                is ReminderUiState.Success -> {

                    if (
                        state.reminders
                            .isEmpty()
                    ) {
                        ReminderEmptyState()
                    } else {

                        LazyColumn(
                            modifier =
                                Modifier.fillMaxSize(),

                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 20.dp,
                                    bottom = 100.dp
                                ),

                            verticalArrangement =
                                Arrangement.spacedBy(
                                    12.dp
                                )
                        ) {

                            items(
                                items =
                                    state.reminders,

                                key = {
                                    it.id
                                }
                            ) { reminder ->

                                ReminderItem(
                                    reminder =
                                        reminder,

                                    onClick = {

                                        /*
                                         * Load dữ liệu reminder
                                         * vào form edit.
                                         */
                                        viewModel.startEdit(
                                            reminder.id
                                        )

                                        showEditor =
                                            true
                                    },

                                    onActiveChange = {
                                            isActive ->

                                        viewModel
                                            .toggleReminder(
                                                reminderId =
                                                    reminder.id,

                                                isActive =
                                                    isActive
                                            )
                                    },

                                    onDeleteClick = {
                                        reminderToDelete =
                                            reminder
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * Delete confirmation
     * ---------------------------------------------------------
     */

    reminderToDelete?.let {
            reminder ->

        AlertDialog(
            onDismissRequest = {
                reminderToDelete =
                    null
            },

            title = {
                Text(
                    text =
                        stringResource(
                            StringRes.delete_reminder_title
                        )
                )
            },

            text = {
                Text(
                    text =
                        stringResource(
                            StringRes.delete_reminder_message,
                            reminder.title
                        )
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        viewModel
                            .deleteReminder(
                                reminder.id
                            )

                        reminderToDelete =
                            null
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                StringRes.delete
                            ),

                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        reminderToDelete =
                            null
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                StringRes.cancel
                            )
                    )
                }
            }
        )
    }
}

/*
 * =============================================================
 * HEADER
 * =============================================================
 */

@Composable
private fun ReminderHeader(
    onOpenDrawer: () -> Unit
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
                        bottomStart =
                            32.dp,

                        bottomEnd =
                            32.dp
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
                onClick =
                    onOpenDrawer
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Menu,

                    contentDescription =
                        stringResource(
                            StringRes.back
                        ),

                    tint =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer,

                    modifier =
                        Modifier.size(
                            28.dp
                        )
                )
            }

            Spacer(
                modifier =
                    Modifier.size(
                        12.dp
                    )
            )

            Text(
                text =
                    stringResource(
                        StringRes.reminder_title
                    ),

                color =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer,

                fontSize =
                    24.sp,

                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

/*
 * =============================================================
 * REMINDER ITEM
 * =============================================================
 */

@Composable
private fun ReminderItem(
    reminder: ReminderModel,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {

    val formattedDate =
        remember(
            reminder.triggerAt
        ) {
            DateFormat
                .getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                )
                .format(
                    Date(
                        reminder.triggerAt
                    )
                )
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(
                if (
                    reminder.isActive
                ) {
                    1f
                } else {
                    0.6f
                }
            ),

        shape =
            RoundedCornerShape(
                18.dp
            ),

        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surface
                ),

        elevation =
            CardDefaults
                .cardElevation(
                    defaultElevation =
                        1.dp
                ),

        onClick =
            onClick
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    16.dp
                )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier.size(
                            44.dp
                        ),

                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            Icons.Default
                                .Notifications,

                        contentDescription =
                            null,

                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            horizontal =
                                12.dp
                        )
                ) {

                    Text(
                        text =
                            reminder.title,

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight
                                .SemiBold,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )

                    Text(
                        text =
                            formattedDate,

                        fontSize =
                            13.sp,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Text(
                        text =
                            stringResource(
                                repeatLabelRes(
                                    reminder
                                        .repeatRule
                                )
                            ),

                        fontSize =
                            13.sp,

                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
                }

                Switch(
                    checked =
                        reminder.isActive,

                    onCheckedChange =
                        onActiveChange
                )
            }

            if (
                !reminder.note
                    .isNullOrBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )

                Text(
                    text =
                        reminder.note,

                    fontSize =
                        14.sp,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                IconButton(
                    onClick =
                        onDeleteClick
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Delete,

                        contentDescription =
                            stringResource(
                                StringRes.delete
                            ),

                        tint =
                            MaterialTheme
                                .colorScheme
                                .error
                    )
                }
            }
        }
    }
}

/*
 * =============================================================
 * EMPTY STATE
 * =============================================================
 */

@Composable
private fun ReminderEmptyState() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                32.dp
            ),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Icon(
            imageVector =
                Icons.Default
                    .Notifications,

            contentDescription =
                null,

            modifier =
                Modifier.size(
                    64.dp
                ),

            tint =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
                    .copy(
                        alpha = 0.5f
                    )
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        Text(
            text =
                stringResource(
                    StringRes.no_reminders
                ),

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        Text(
            text =
                stringResource(
                    StringRes.no_reminders_hint
                ),

            fontSize =
                13.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
                    .copy(
                        alpha = 0.7f
                    )
        )
    }
}

/*
 * =============================================================
 * NOT AUTHENTICATED
 * =============================================================
 */

@Composable
private fun ReminderLoginRequired() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                32.dp
            ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                stringResource(
                    StringRes.reminder_login_required
                ),

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

/*
 * =============================================================
 * REPEAT LABEL
 * =============================================================
 */

private fun repeatLabelRes(
    repeatRule: RepeatRule
): Int {

    return when (
        repeatRule
    ) {

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