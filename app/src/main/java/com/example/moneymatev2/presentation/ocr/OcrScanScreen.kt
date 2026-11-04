package com.example.moneymatev2.presentation.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.example.moneymatev2.R
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.ReceiptModel
import com.example.moneymatev2.ui.item.categoryDisplayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScanScreen(
    viewModel: OcrViewModel,
    categories: List<CategoryModel>,
    ownerId: String?,
    onBack: () -> Unit,
    onUse: (ReceiptModel, String?) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var confirmUncertain by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        showCamera = granted
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.scan(it, categories, ownerId) }
    }
    BackHandler(showCamera) { showCamera = false }
    if (showCamera) {
        OcrCameraScreen(onClose = { showCamera = false }, onCaptured = { file ->
            showCamera = false
            viewModel.scan(Uri.fromFile(file), categories, ownerId, file)
        })
        return
    }
    if (confirmUncertain && state.phase == OcrPhase.UNCERTAIN && state.receipt != null) {
        AlertDialog(
            onDismissRequest = { confirmUncertain = false },
            title = { Text(stringResource(R.string.ocr_uncertain_confirm)) },
            text = { Text(stringResource(R.string.ocr_uncertain)) },
            confirmButton = { TextButton(onClick = {
                confirmUncertain = false
                state.receipt?.let { onUse(it, state.ownerId) }
            }) { Text(stringResource(R.string.ocr_confirm_use)) } },
            dismissButton = { TextButton(onClick = { confirmUncertain = false }) {
                Text(stringResource(R.string.ocr_back))
            } }
        )
    }
    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ocr_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }) { padding ->
      BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val cardMinHeight = (maxHeight - 170.dp).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 24.dp)
            ) {
                OutlinedButton(onClick = {
                    permissionDenied = false
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        showCamera = true else cameraPermission.launch(Manifest.permission.CAMERA)
                }, enabled = state.phase != OcrPhase.PROCESSING,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.ocr_take_photo),
                        modifier = Modifier.padding(start = 6.dp), maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = state.phase != OcrPhase.PROCESSING,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.ocr_choose_image),
                        modifier = Modifier.padding(start = 6.dp), maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)) {
            if (permissionDenied) Text(stringResource(R.string.ocr_camera_permission),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            when (state.phase) {
                OcrPhase.IDLE -> Text(stringResource(R.string.ocr_idle),
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OcrPhase.IMAGE_SELECTED -> Text(stringResource(R.string.ocr_image_selected))
                OcrPhase.PROCESSING -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ocr_processing))
                }
                OcrPhase.NO_TEXT -> Text(stringResource(R.string.ocr_no_text))
                OcrPhase.NOT_RECEIPT -> Text(stringResource(R.string.ocr_not_receipt))
                OcrPhase.ERROR -> Text(stringResource(R.string.ocr_error), color = MaterialTheme.colorScheme.error)
                OcrPhase.SUCCESS, OcrPhase.INCOMPLETE, OcrPhase.UNCERTAIN -> {
                    val receipt = state.receipt
                    if (receipt != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = cardMinHeight),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.fillMaxWidth().heightIn(min = cardMinHeight).padding(16.dp)) {
                                Text(stringResource(R.string.ocr_result),
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                ResultRow(stringResource(R.string.ocr_amount), receipt.totalAmount?.let {
                                    "${CurrencyFormatter.formatFull(it)} đ"
                                } ?: stringResource(R.string.ocr_unknown))
                                Spacer(Modifier.height(12.dp))
                                ResultRow(stringResource(R.string.ocr_date), receipt.transactionDate?.let {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
                                } ?: stringResource(R.string.ocr_unknown))
                                Spacer(Modifier.height(18.dp))
                                ResultRow(stringResource(R.string.ocr_category), categories.firstOrNull {
                                    it.id == receipt.suggestedCategoryId && it.userId == ownerId &&
                                        it.type == TransactionType.EXPENSE && !it.isArchived && !it.isMissing
                                }?.let { categoryDisplayName(it) } ?: stringResource(R.string.ocr_unknown))
                                Spacer(Modifier.height(18.dp))
                                ResultRow(stringResource(R.string.ocr_note), receipt.note ?: stringResource(R.string.ocr_unknown))
                                Spacer(Modifier.height(18.dp))
                                ResultRow(stringResource(R.string.ocr_confidence), stringResource(R.string.ocr_verify))
                                if (state.phase == OcrPhase.INCOMPLETE) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.ocr_incomplete),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (state.phase == OcrPhase.UNCERTAIN) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.ocr_uncertain),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.height(14.dp))
                                OutlinedButton(onClick = viewModel::discard,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                    Text(stringResource(R.string.ocr_discard))
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = {
                                    if (state.phase == OcrPhase.UNCERTAIN) confirmUncertain = true
                                    else onUse(receipt, state.ownerId)
                                },
                                    enabled = state.ownerId != null && state.ownerId == ownerId,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                    Text(stringResource(R.string.ocr_use_result))
                                }
                            }
                        }
                    }
                }
            }
            if (state.phase == OcrPhase.ERROR || state.phase == OcrPhase.NO_TEXT || state.phase == OcrPhase.NOT_RECEIPT) {
                OutlinedButton(onClick = viewModel::discard) { Text(stringResource(R.string.ocr_retry)) }
            }
            }
        }
      }
        }
}
@Composable
private fun ResultRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
