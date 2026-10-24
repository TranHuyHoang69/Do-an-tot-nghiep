package com.example.moneymatev2.presentation.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.security.LEGACY_PIN_LENGTH
import com.example.moneymatev2.presentation.theme.StringResource
import kotlinx.coroutines.launch

/** The layout deliberately follows the original MoneyMate Security screen. */
@Composable
fun SecurityScreen(
    onOpenDrawer: () -> Unit,
    onCreatePin: () -> Unit,
    onChangePin: () -> Unit,
    onDeletePin: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val state by viewModel.securityState.collectAsState()
    val active = state as? LocalSecurityState.Active
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var biometricStatus by remember(active?.hasPin) {
        mutableStateOf(if (active?.hasPin == true) BiometricAuthHelper.canAuthenticate(context) else null)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, active?.hasPin) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active?.hasPin == true)
                biometricStatus = BiometricAuthHelper.canAuthenticate(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val biometricAvailable = biometricStatus?.let(BiometricAuthHelper::isAvailable) == true
    val promptTitle = StringResource(StringRes.security_bio_prompt_title)
    val promptSubtitle = StringResource(StringRes.security_bio_prompt_subtitle)
    val cancel = StringResource(StringRes.security_cancel)

    LaunchedEffect(active?.biometricEnabled, biometricStatus) {
        if (active?.biometricEnabled == true && !biometricAvailable) viewModel.toggleBiometric(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp).height(56.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, StringResource(StringRes.security_menu_desc),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(StringResource(StringRes.security_settings),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(StringResource(StringRes.security_section_pin_title), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            if (active == null) {
                Text(StringResource(StringRes.security_login_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!active.hasPin) {
                SecurityItemRow(StringResource(StringRes.security_setup_pin_title),
                    StringResource(StringRes.security_setup_pin_subtitle), Icons.Default.Lock,
                    MaterialTheme.colorScheme.primary, onCreatePin)
            } else {
                SecurityItemRow(StringResource(StringRes.security_change_pin_title),
                    StringResource(if (active.pinLength == LEGACY_PIN_LENGTH)
                        StringRes.security_migrate_pin_subtitle else StringRes.security_change_pin_subtitle),
                    Icons.Default.LockReset,
                    MaterialTheme.colorScheme.primary, onChangePin)
                SecurityItemRow(StringResource(StringRes.security_delete_pin_title),
                    StringResource(StringRes.security_delete_pin_subtitle), Icons.Default.Delete,
                    MaterialTheme.colorScheme.error, onDeletePin)
            }

            Spacer(Modifier.height(12.dp))
            if (active?.hasPin == true) {
                Text(StringResource(StringRes.security_section_biometric_title), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, StringResource(StringRes.security_bio_icon_desc),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(StringResource(StringRes.security_use_biometric_title), fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(StringResource(StringRes.security_use_biometric_subtitle), fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = active.biometricEnabled && biometricAvailable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            onCheckedChange = { checked ->
                                if (!checked) scope.launch { viewModel.toggleBiometric(false) }
                                else {
                                    val status = BiometricAuthHelper.canAuthenticate(context)
                                    biometricStatus = status
                                    val activity = BiometricAuthHelper.findFragmentActivity(context)
                                    when {
                                        !BiometricAuthHelper.isAvailable(status) ->
                                            Toast.makeText(context, BiometricAuthHelper.statusMessage(status), Toast.LENGTH_SHORT).show()
                                        activity == null -> Toast.makeText(context,
                                            StringRes.security_bio_host_error, Toast.LENGTH_SHORT).show()
                                        else -> BiometricAuthHelper.prompt(activity, promptTitle, promptSubtitle, cancel,
                                            onSuccess = { scope.launch { viewModel.toggleBiometric(true) } },
                                            onError = { code, _ ->
                                                if (!BiometricAuthHelper.isCancellation(code))
                                                    Toast.makeText(context, StringRes.security_bio_unavailable, Toast.LENGTH_SHORT).show()
                                            },
                                            onFailed = { Toast.makeText(context,
                                                StringRes.security_bio_failed, Toast.LENGTH_SHORT).show() })
                                    }
                                }
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityItemRow(title: String, subtitle: String, icon: ImageVector,
                            iconColor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                StringResource(StringRes.security_navigation_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
