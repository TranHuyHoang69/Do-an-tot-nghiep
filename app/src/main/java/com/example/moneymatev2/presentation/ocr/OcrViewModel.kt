package com.example.moneymatev2.presentation.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.ocr.MlKitOcrProcessor
import com.example.moneymatev2.data.ocr.ReceiptAnalyzer
import com.example.moneymatev2.data.ocr.ReceiptValidationStatus
import com.example.moneymatev2.data.ocr.ReceiptValidator
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.ReceiptModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class OcrPhase { IDLE, IMAGE_SELECTED, PROCESSING, SUCCESS, UNCERTAIN, NOT_RECEIPT, NO_TEXT, INCOMPLETE, ERROR }
data class OcrUiState(val phase: OcrPhase = OcrPhase.IDLE, val receipt: ReceiptModel? = null,
                      val ownerId: String? = null)

internal fun validatedOcrState(status: ReceiptValidationStatus, receipt: ReceiptModel?, ownerId: String?): OcrUiState =
    when (status) {
        ReceiptValidationStatus.NO_TEXT -> OcrUiState(OcrPhase.NO_TEXT, ownerId = ownerId)
        ReceiptValidationStatus.NOT_RECEIPT -> OcrUiState(OcrPhase.NOT_RECEIPT, ownerId = ownerId)
        ReceiptValidationStatus.UNCERTAIN -> OcrUiState(OcrPhase.UNCERTAIN, receipt, ownerId)
        ReceiptValidationStatus.VALID -> OcrUiState(
            if (receipt?.totalAmount == null || receipt.transactionDate == null) OcrPhase.INCOMPLETE
            else OcrPhase.SUCCESS, receipt, ownerId)
    }

@HiltViewModel
class OcrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: MlKitOcrProcessor
) : ViewModel() {
    private val analyzer = ReceiptAnalyzer()
    private val validator = ReceiptValidator(analyzer)
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state
    private var generation = 0

    fun scan(uri: Uri, categories: List<CategoryModel>, ownerId: String?, cameraFile: File? = null) {
        if (_state.value.phase == OcrPhase.PROCESSING) {
            cameraFile?.delete()
            return
        }
        val request = ++generation
        _state.value = OcrUiState(OcrPhase.IMAGE_SELECTED, ownerId = ownerId)
        viewModelScope.launch {
            _state.value = OcrUiState(OcrPhase.PROCESSING, ownerId = ownerId)
            try {
                val text = processor.recognize(context, uri)
                if (request != generation) return@launch
                val validation = withContext(Dispatchers.Default) { validator.validate(text) }
                if (request != generation) return@launch
                when (validation.status) {
                    ReceiptValidationStatus.NO_TEXT ->
                        _state.value = validatedOcrState(validation.status, null, ownerId)
                    ReceiptValidationStatus.NOT_RECEIPT ->
                        _state.value = validatedOcrState(validation.status, null, ownerId)
                    ReceiptValidationStatus.VALID, ReceiptValidationStatus.UNCERTAIN -> {
                        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                        val trace: ((String) -> Unit)? = if (debug) {
                            text.fullText.chunked(3000).forEach { Log.d("ReceiptOCR", "raw text: $it") }
                            Log.d("ReceiptOCR", "validation: ${validation.status}; ${validation.reason}")
                            val logger: (String) -> Unit = { message -> Log.d("ReceiptOCR", message) }
                            logger
                        } else null
                        val receipt = withContext(Dispatchers.Default) {
                            analyzer.analyze(text, categories, ownerId, trace)
                        }
                        if (request != generation) return@launch
                        _state.value = validatedOcrState(validation.status, receipt, ownerId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == generation) _state.value = OcrUiState(OcrPhase.ERROR, ownerId = ownerId)
            } finally {
                cameraFile?.delete()
            }
        }
    }

    fun discard() {
        generation++
        _state.value = OcrUiState()
    }
}
