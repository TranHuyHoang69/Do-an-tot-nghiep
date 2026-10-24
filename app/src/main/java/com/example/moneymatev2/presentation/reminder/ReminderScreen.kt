package com.example.moneymatev2.presentation.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.reminder.ReminderNotificationAvailability
import com.example.moneymatev2.data.reminder.ReminderNotificationManager
import com.example.moneymatev2.data.reminder.ReminderNotificationStatus
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.presentation.root.PendingReminderNavigation
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ReminderScreen(
    onOpenDrawer: () -> Unit,
    openRequest: PendingReminderNavigation? = null,
    onOpenRequestResolved: (Long) -> Unit = {},
    onBackDuringOpen: () -> Unit = {},
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationStatus by remember {
        mutableStateOf(ReminderNotificationAvailability.status(context))
    }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var requestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<List<String>?>(null) }
    var deleteConfirmationSubmitted by remember { mutableStateOf(false) }
    val isDeleting by viewModel.isDeleting.collectAsState()

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationStatus = ReminderNotificationAvailability.status(context)
                if (notificationStatus == ReminderNotificationStatus.AVAILABLE) {
                    showNotificationDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showEditor by rememberSaveable {
        mutableStateOf(false)
    }
    var loadedRequestId by remember { mutableStateOf<Long?>(null) }
    var openedFromNotification by rememberSaveable { mutableStateOf(false) }

    // A restored screen must not show an empty editor after its ViewModel was recreated.
    LaunchedEffect(openedFromNotification, formState.editingReminderId, openRequest) {
        if (openedFromNotification && formState.editingReminderId == null && openRequest == null) {
            showEditor = false
            openedFromNotification = false
        }
    }

    BackHandler(enabled = showEditor &&
        (openRequest == null || loadedRequestId == openRequest.requestId)) {
        showEditor = false
        openedFromNotification = false
        viewModel.startCreate()
    }
    BackHandler(enabled = openRequest != null && loadedRequestId != openRequest.requestId) {
        openRequest?.let { onOpenRequestResolved(it.requestId) }
        onBackDuringOpen()
    }

    var showExactAlarmDialog by remember {
        mutableStateOf(false)
    }

    val selectedReminderIds =
        remember {
            mutableStateListOf<String>()
        }

    val isSelectionMode =
        selectedReminderIds.isNotEmpty()

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    val missingReminderMessage = stringResource(StringRes.reminder_not_found)
    val cannotOpenReminderMessage = stringResource(StringRes.reminder_cannot_open)

    LaunchedEffect(openRequest?.requestId) {
        val request = openRequest ?: return@LaunchedEffect
        loadedRequestId = null
        showEditor = false
        val result = viewModel.prepareNotificationEdit(request.reminderId, request.ownerUid)
        if (result == ReminderOpenResult.Ready) {
            loadedRequestId = request.requestId
            openedFromNotification = true
            showEditor = true
        } else {
            viewModel.startCreate()
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (result == ReminderOpenResult.Missing) missingReminderMessage
                    else cannotOpenReminderMessage
                )
            }
        }
        onOpenRequestResolved(request.requestId)
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

    val saveErrorMessage = stringResource(StringRes.reminder_save_error)
    val updateErrorMessage = stringResource(StringRes.reminder_update_error)
    val deleteErrorMessage = stringResource(StringRes.reminder_delete_error)

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationStatus = ReminderNotificationAvailability.status(context)
        showNotificationDialog = !granted || notificationStatus != ReminderNotificationStatus.AVAILABLE
    }

    val openCreateReminder: () -> Unit = {
        viewModel.startCreate()
        showEditor = true
        notificationStatus = ReminderNotificationAvailability.status(context)
        if (notificationStatus != ReminderNotificationStatus.AVAILABLE) {
            showNotificationDialog = true
        }
    }

    val openNotificationSettings: () -> Unit = {
        val intent = if (notificationStatus == ReminderNotificationStatus.CHANNEL_DISABLED) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotificationManager.CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
        context.startActivity(intent)
        showNotificationDialog = false
    }
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

    fun toggleReminderSelection(
        reminderId: String
    ) {
        if (
            selectedReminderIds.contains(
                reminderId
            )
        ) {
            selectedReminderIds.remove(
                reminderId
            )
        } else {
            selectedReminderIds.add(
                reminderId
            )
        }
    }

    fun clearReminderSelection() {
        selectedReminderIds.clear()
    }

    /*
     * ---------------------------------------------------------
     * One-shot events từ ViewModel
     * ---------------------------------------------------------
     */

    LaunchedEffect(
        viewModel, savedMessage, deletedMessage, invalidTitleMessage,
        invalidTimeMessage, loginRequiredMessage, expiredMessage,
        missingReminderMessage, saveErrorMessage, updateErrorMessage, deleteErrorMessage
    ) {
        viewModel.events.collect { event ->

            when (event) {

                ReminderEvent.Saved -> {

                    // Đóng Editor ngay lập tức
                    showEditor = false
                    openedFromNotification = false

                    // Không block collector event
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            savedMessage
                        )
                    }
                }

                ReminderEvent.Deleted -> {

                    pendingDeleteIds = null
                    deleteConfirmationSubmitted = false

                    clearReminderSelection()

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
                    if (event.kind == ReminderErrorKind.DELETE) {
                        pendingDeleteIds = null
                        deleteConfirmationSubmitted = false
                    }
                    val message = when (event.kind) {
                        ReminderErrorKind.MISSING -> missingReminderMessage
                        ReminderErrorKind.SAVE -> saveErrorMessage
                        ReminderErrorKind.UPDATE -> updateErrorMessage
                        ReminderErrorKind.DELETE -> deleteErrorMessage
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message
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

    if (showNotificationDialog && notificationStatus != ReminderNotificationStatus.AVAILABLE) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text(stringResource(StringRes.reminder_notification_blocked_title)) },
            text = { Text(stringResource(StringRes.reminder_notification_blocked_message)) },
            confirmButton = {
                val canRequest = notificationStatus == ReminderNotificationStatus.RUNTIME_PERMISSION &&
                    !requestedNotificationPermission
                TextButton(onClick = {
                    if (canRequest) {
                        requestedNotificationPermission = true
                        showNotificationDialog = false
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings()
                    }
                }) {
                    Text(stringResource(
                        if (canRequest) StringRes.reminder_enable_notifications
                        else StringRes.reminder_open_settings
                    ))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text(stringResource(StringRes.reminder_later))
                }
            }
        )
    }

    pendingDeleteIds?.let { ids ->
        ReminderDeleteConfirmationDialog(
            count = ids.size,
            inProgress = deleteConfirmationSubmitted || isDeleting,
            onDismiss = { pendingDeleteIds = null },
            onConfirm = {
                if (viewModel.deleteReminders(ids)) deleteConfirmationSubmitted = true
            }
        )
    }

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

    if (showEditor && (openRequest == null || loadedRequestId == openRequest.requestId)) {
        ReminderEditorScreen(
            formState = formState,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBackClick = {
                showEditor = false
                openedFromNotification = false
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
                        ReminderUiState.NotAuthenticated &&
                !isSelectionMode
            ) {
                FloatingActionButton(
                    onClick = {

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
        }    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    innerPadding
                )
        ) {

            ReminderHeader(
                selectionCount =
                    selectedReminderIds.size,

                onOpenDrawer =
                    onOpenDrawer,

                onCancelSelection = {
                    clearReminderSelection()
                },

                onDeleteSelection = {
                    if (!deleteConfirmationSubmitted && !isDeleting) {
                        val idsToDelete = snapshotReminderDeleteIds(
                            selectedReminderIds,
                            (uiState as? ReminderUiState.Success)?.reminders.orEmpty()
                        )
                        if (idsToDelete.isNotEmpty()) pendingDeleteIds = idsToDelete
                    }
                }
            )

            if (notificationStatus != ReminderNotificationStatus.AVAILABLE) {
                Text(
                    text = stringResource(StringRes.reminder_notification_blocked_banner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNotificationDialog = true }
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp
                )
            }

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
                                stringResource(StringRes.reminder_load_error),

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
                            modifier = Modifier.fillMaxSize(),

                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp
                            ),

                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {


                            items(
                                items = state.reminders,
                                key = { it.id }
                            ) { reminder ->

                                ReminderItem(
                                    reminder = reminder,

                                    isSelectionMode = isSelectionMode,

                                    isSelected = selectedReminderIds.contains(reminder.id),

                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleReminderSelection(reminder.id)
                                        } else {
                                            viewModel.startEdit(reminder.id)
                                            showEditor = true
                                        }
                                    },

                                    onLongClick = {
                                        if (!selectedReminderIds.contains(reminder.id)) {
                                            selectedReminderIds.add(reminder.id)
                                        }
                                    },

                                    onActiveChange = { isActive ->
                                        if (!isSelectionMode) {
                                            if (isActive) {
                                                notificationStatus = ReminderNotificationAvailability.status(context)
                                                if (notificationStatus != ReminderNotificationStatus.AVAILABLE) {
                                                    showNotificationDialog = true
                                                }
                                            }
                                            viewModel.toggleReminder(
                                                reminderId = reminder.id,
                                                isActive = isActive
                                            )
                                        }
                                    }
                                )
                            }

                        }
                    }
                }
            }
        }
    }

}

/*
 * =============================================================
 * HEADER
 * =============================================================
 */

@Composable
private fun ReminderHeader(
    selectionCount: Int,
    onOpenDrawer: () -> Unit,
    onCancelSelection: () -> Unit,
    onDeleteSelection: () -> Unit
) {
    val isSelectionMode =
        selectionCount > 0

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

        if (isSelectionMode) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                TextButton(
                    onClick =
                        onCancelSelection
                ) {
                    Text(
                        text =
                            stringResource(
                                StringRes.cancel
                            ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                        fontSize =
                            17.sp
                    )
                }

                Text(
                    text =
                        stringResource(
                            StringRes.reminder_selected_count,
                            selectionCount
                        ),

                    modifier =
                        Modifier.weight(1f),

                    color =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer,

                    fontSize =
                        22.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )

                IconButton(
                    onClick =
                        onDeleteSelection
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

        } else {

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
                                StringRes.reminder_title
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
}
/*
 * =============================================================
 * REMINDER ITEM
 * =============================================================
 */


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderItem(
    reminder: ReminderModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    val formattedDate = remember(reminder.triggerAt) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT
        ).format(Date(reminder.triggerAt))
    }

    val colorScheme = MaterialTheme.colorScheme

    // Màu bám theo theme của ứng dụng.
    // Khi chọn, card chuyển sang tông xanh như ảnh mẫu.
    val cardColor = when {
        isSelected ->
            if (colorScheme.background.luminance() < 0.5f) {
                Color(0xFF164735)
            } else {
                colorScheme.primaryContainer
            }

        else -> colorScheme.surfaceContainerHigh
    }

    val textColor = if (
        isSelected &&
        colorScheme.background.luminance() < 0.5f
    ) {
        Color.White
    } else {
        colorScheme.onSurface
    }

    val secondaryTextColor = textColor.copy(alpha = 0.68f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (reminder.isActive || isSelectionMode) 1f else 0.6f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 94.dp)
                .padding(
                    horizontal = if (isSelectionMode) 12.dp else 18.dp,
                    vertical = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Checkbox chỉ xuất hiện khi vào chế độ chọn.
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        onClick()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorScheme.tertiary,
                        uncheckedColor = secondaryTextColor,
                        checkmarkColor = colorScheme.onTertiary
                    )
                )

                Spacer(
                    modifier = Modifier.width(12.dp)
                )
            }

            // Nội dung lời nhắc.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = reminder.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = secondaryTextColor
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = formattedDate,
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Chỉ hiện nhãn lặp nếu Reminder có lặp.
                if (reminder.isActive && reminder.repeatRule == RepeatRule.NONE &&
                    reminder.triggerAt <= System.currentTimeMillis()
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(StringRes.reminder_missed_once_hint),
                        fontSize = 12.sp,
                        color = colorScheme.error
                    )
                }

                if (reminder.repeatRule != RepeatRule.NONE) {
                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = stringResource(
                            repeatLabelRes(reminder.repeatRule)
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) {
                            textColor
                        } else {
                            colorScheme.primary
                        }
                    )
                }

                // Chỉ hiện ghi chú khi người dùng đã nhập.
                if (!reminder.note.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = reminder.note,
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bình thường hiện Switch, khi chọn thì ẩn.
            if (!isSelectionMode) {
                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Switch(
                    checked = reminder.isActive,
                    onCheckedChange = onActiveChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colorScheme.primary,
                        checkedThumbColor = colorScheme.onPrimary
                    )
                )
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

        RepeatRule.YEARLY ->
            StringRes.repeat_yearly
    }
}

@Composable
internal fun ReminderDeleteConfirmationDialog(
    count: Int,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(stringResource(StringRes.delete_reminder_title)) },
        text = {
            Text(
                if (count == 1) stringResource(StringRes.reminder_delete_one_confirm)
                else stringResource(StringRes.reminder_delete_many_confirm, count)
            )
        },
        confirmButton = {
            TextButton(enabled = !inProgress && count > 0, onClick = onConfirm) {
                Text(stringResource(StringRes.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !inProgress, onClick = onDismiss) {
                Text(stringResource(StringRes.cancel))
            }
        }
    )
}
