package com.example.moneymatev2.presentation.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.security.CURRENT_PIN_LENGTH
import com.example.moneymatev2.data.security.LEGACY_PIN_LENGTH
import com.example.moneymatev2.presentation.theme.StringResource
import kotlinx.coroutines.launch

enum class PinMode { CREATE, CHANGE, DELETE }

@Composable
fun PinActionScreen(mode: PinMode, onBack: () -> Unit,
                    viewModel: SecurityViewModel = hiltViewModel()) {
    val state by viewModel.securityState.collectAsState()
    val active = state as? LocalSecurityState.Active
    if (active == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // PIN input is only held by this composition and is discarded on account changes.
    key(active.uid) {
        when (mode) {
            PinMode.CREATE -> CreatePinScreen(onBack, viewModel)
            PinMode.CHANGE -> ChangePinScreen(active.pinLength, onBack, viewModel)
            PinMode.DELETE -> DeletePinScreen(active.pinLength, onBack, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, StringResource(StringRes.security_back_desc))
        } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
}

@Composable
private fun CreatePinScreen(onBack: () -> Unit, viewModel: SecurityViewModel) {
    var confirming by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SecurityResult?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = StringResource(StringRes.security_setup_pin_title)

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { PinTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                Text(StringResource(if (confirming) StringRes.security_confirm_new_pin_hint
                    else StringRes.security_enter_new_pin_hint), fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(StringResource(StringRes.security_pin_purpose_desc), fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                PinDots(input.length, CURRENT_PIN_LENGTH, 20.dp,
                    MaterialTheme.colorScheme.primary, 16.dp, outlined = false)
                Spacer(Modifier.height(40.dp))
                PinKeypad(keySize = 76.dp, horizontalGap = 16.dp, enabled = !processing,
                    createStyle = true,
                    onDigit = { digit ->
                        if (input.length < CURRENT_PIN_LENGTH) {
                            val updated = input + digit
                            input = updated; error = null
                            if (updated.length == CURRENT_PIN_LENGTH) {
                                if (!confirming) {
                                    firstPin = updated; input = ""; confirming = true
                                } else if (updated != firstPin) {
                                    Toast.makeText(context, StringRes.security_pin_mismatch,
                                        Toast.LENGTH_SHORT).show()
                                    firstPin = ""; input = ""; confirming = false
                                    error = SecurityResult.MISMATCH
                                } else {
                                    processing = true
                                    scope.launch {
                                        val result = viewModel.create(firstPin, updated)
                                        firstPin = ""; input = ""; confirming = false
                                        processing = false
                                        if (result == SecurityResult.SUCCESS) {
                                            Toast.makeText(context, StringRes.security_success,
                                                Toast.LENGTH_SHORT).show()
                                            onBack()
                                        } else error = result
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (input.isNotEmpty()) input = input.dropLast(1)
                        else if (confirming) { confirming = false; firstPin = "" }
                    })
                error?.let { Spacer(Modifier.height(16.dp)); PinError(it) }
            }
            if (processing) PinBusyOverlay()
        }
    }
}

private enum class ChangeStep { OLD, NEW, CONFIRM }

@Composable
private fun ChangePinScreen(oldLength: Int, onBack: () -> Unit, viewModel: SecurityViewModel) {
    var step by remember { mutableStateOf(ChangeStep.OLD) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SecurityResult?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = StringResource(StringRes.security_change_pin_title)
    val requiredLength = if (step == ChangeStep.OLD) oldLength else CURRENT_PIN_LENGTH

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { PinTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(StringResource(when (step) {
                        ChangeStep.OLD -> StringRes.security_enter_old_pin_hint
                        ChangeStep.NEW -> StringRes.security_enter_new_pin_hint
                        ChangeStep.CONFIRM -> StringRes.security_confirm_new_pin_hint
                    }), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(StringResource(StringRes.security_pin_length_hint, requiredLength),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    PinDots(input.length, requiredLength, 16.dp,
                        MaterialTheme.colorScheme.primary, 24.dp, outlined = true)
                    error?.let { Spacer(Modifier.height(16.dp)); PinError(it) }
                }
                Column(Modifier.padding(bottom = 20.dp, top = 24.dp)) {
                    PinKeypad(76.dp, 16.dp, enabled = !processing, createStyle = false,
                        onDigit = { digit ->
                            if (input.length < requiredLength) {
                                val updated = input + digit
                                input = updated; error = null
                                if (updated.length == requiredLength) when (step) {
                                    ChangeStep.OLD -> {
                                        oldPin = updated; input = ""; step = ChangeStep.NEW
                                    }
                                    ChangeStep.NEW -> {
                                        newPin = updated; input = ""; step = ChangeStep.CONFIRM
                                    }
                                    ChangeStep.CONFIRM -> {
                                        if (updated != newPin) {
                                            Toast.makeText(context, StringRes.security_pin_mismatch,
                                                Toast.LENGTH_SHORT).show()
                                            newPin = ""; input = ""; step = ChangeStep.NEW
                                            error = SecurityResult.MISMATCH
                                        } else {
                                            processing = true
                                            scope.launch {
                                                val result = viewModel.change(oldPin, newPin, updated)
                                                oldPin = ""; newPin = ""; input = ""
                                                step = ChangeStep.OLD; processing = false
                                                if (result == SecurityResult.SUCCESS) {
                                                    Toast.makeText(context, StringRes.security_success,
                                                        Toast.LENGTH_SHORT).show()
                                                    onBack()
                                                } else error = result
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onBackspace = {
                            if (input.isNotEmpty()) input = input.dropLast(1)
                            else when (step) {
                                ChangeStep.CONFIRM -> { newPin = ""; step = ChangeStep.NEW }
                                ChangeStep.NEW -> { oldPin = ""; step = ChangeStep.OLD }
                                ChangeStep.OLD -> Unit
                            }
                        })
                }
            }
            if (processing) PinBusyOverlay()
        }
    }
}

@Composable
private fun DeletePinScreen(oldLength: Int, onBack: () -> Unit, viewModel: SecurityViewModel) {
    var input by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SecurityResult?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = StringResource(StringRes.security_delete_pin_title)

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { PinTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(StringResource(StringRes.security_delete_heading), fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(StringResource(StringRes.security_delete_warning), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(40.dp))
                    PinDots(input.length, oldLength, 16.dp,
                        MaterialTheme.colorScheme.error, 24.dp, outlined = true)
                    error?.let { Spacer(Modifier.height(16.dp)); PinError(it) }
                }
                Column(Modifier.padding(bottom = 20.dp, top = 24.dp)) {
                    PinKeypad(76.dp, 16.dp, enabled = !processing, createStyle = false,
                        onDigit = { digit ->
                            if (input.length < oldLength) {
                                val updated = input + digit
                                input = updated; error = null
                                if (updated.length == oldLength) {
                                    processing = true
                                    scope.launch {
                                        val result = viewModel.delete(updated)
                                        input = ""; processing = false
                                        if (result == SecurityResult.SUCCESS) {
                                            Toast.makeText(context, StringRes.security_success,
                                                Toast.LENGTH_SHORT).show()
                                            onBack()
                                        } else error = result
                                    }
                                }
                            }
                        },
                        onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) })
                }
            }
            if (processing) PinBusyOverlay()
        }
    }
}

@Composable
fun SecurityLockScreen(state: LocalSecurityState.Active,
                       viewModel: SecurityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember(state.uid) { mutableStateOf("") }
    var error by remember(state.uid) { mutableStateOf<SecurityResult?>(null) }
    var processing by remember(state.uid) { mutableStateOf(false) }
    var recoveryDialog by remember(state.uid) { mutableStateOf(false) }
    var biometricPromptShown by remember(state.uid) { mutableStateOf(false) }
    val promptTitle = StringResource(StringRes.security_bio_prompt_title)
    val promptSubtitle = StringResource(StringRes.security_bio_unlock_subtitle)
    val cancel = StringResource(StringRes.security_cancel)
    val biometricAvailable = state.biometricEnabled &&
        BiometricAuthHelper.isAvailable(BiometricAuthHelper.canAuthenticate(context))

    val showBiometricPrompt: () -> Unit = {
        val status = BiometricAuthHelper.canAuthenticate(context)
        val activity = BiometricAuthHelper.findFragmentActivity(context)
        when {
            !BiometricAuthHelper.isAvailable(status) -> Toast.makeText(context,
                BiometricAuthHelper.statusMessage(status), Toast.LENGTH_SHORT).show()
            activity == null -> Toast.makeText(context, StringRes.security_bio_host_error,
                Toast.LENGTH_SHORT).show()
            else -> BiometricAuthHelper.prompt(activity, promptTitle, promptSubtitle, cancel,
                onSuccess = { viewModel.unlockWithBiometric(state.uid) },
                onError = { code, _ -> if (!BiometricAuthHelper.isCancellation(code))
                    Toast.makeText(context, StringRes.security_bio_unavailable,
                        Toast.LENGTH_SHORT).show() },
                onFailed = { Toast.makeText(context, StringRes.security_bio_failed,
                    Toast.LENGTH_SHORT).show() })
        }
    }

    LaunchedEffect(state.uid, biometricAvailable) {
        if (biometricAvailable && !biometricPromptShown && !state.needsRecovery) {
            biometricPromptShown = true
            showBiometricPrompt()
        }
    }

    if (recoveryDialog) AlertDialog(onDismissRequest = { recoveryDialog = false },
        title = { Text(StringResource(StringRes.security_recovery_title)) },
        text = { Text(StringResource(StringRes.security_recovery_message)) },
        confirmButton = { TextButton(onClick = {
            recoveryDialog = false
            scope.launch { if (!viewModel.beginRecovery()) error = SecurityResult.FAILED }
        }) { Text(StringResource(StringRes.security_recovery_confirm)) } },
        dismissButton = { TextButton(onClick = { recoveryDialog = false }) {
            Text(StringResource(StringRes.security_cancel))
        } })

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(StringResource(when {
                        state.needsRecovery -> StringRes.security_pin_unavailable
                        state.pinLength == LEGACY_PIN_LENGTH -> StringRes.security_legacy_unlock_hint
                        else -> StringRes.security_lock_subtitle
                    }), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    if (!state.needsRecovery) {
                        Spacer(Modifier.height(40.dp))
                        PinDots(input.length, state.pinLength, 16.dp,
                            MaterialTheme.colorScheme.primary, 24.dp, outlined = true)
                    }
                    error?.let { Spacer(Modifier.height(16.dp)); PinError(it) }
                }
                Column(Modifier.padding(bottom = 20.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!state.needsRecovery) {
                        PinKeypad(76.dp, 16.dp, enabled = !processing, createStyle = false,
                            showBiometric = biometricAvailable,
                            onBiometric = showBiometricPrompt,
                            onDigit = { digit ->
                                if (input.length < state.pinLength) {
                                    val updated = input + digit
                                    input = updated; error = null
                                    if (updated.length == state.pinLength) {
                                        processing = true
                                        scope.launch {
                                            val result = viewModel.verify(updated)
                                            input = ""; processing = false
                                            if (result != SecurityResult.SUCCESS) error = result
                                        }
                                    }
                                }
                            },
                            onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) })
                    }
                    TextButton(onClick = { recoveryDialog = true }) {
                        Text(StringResource(StringRes.security_forgot_pin))
                    }
                }
            }
            if (processing) PinBusyOverlay()
        }
    }
}

@Composable
private fun PinBusyOverlay() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PinError(result: SecurityResult) {
    Text(StringResource(result.messageRes()), color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}

private fun SecurityResult.messageRes(): Int = when (this) {
    SecurityResult.INVALID_PIN -> StringRes.security_invalid_pin
    SecurityResult.MISMATCH -> StringRes.security_pin_mismatch
    SecurityResult.WRONG_PIN -> StringRes.security_wrong_pin
    SecurityResult.LOCKED -> StringRes.security_pin_locked
    SecurityResult.UNAVAILABLE -> StringRes.security_pin_unavailable
    else -> StringRes.security_failed
}
