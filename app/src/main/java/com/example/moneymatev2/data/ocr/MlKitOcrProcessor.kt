package com.example.moneymatev2.data.ocr

import android.content.Context
import android.net.Uri
import com.example.moneymatev2.domain.model.OcrResultModel
import com.example.moneymatev2.domain.model.OcrTextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MlKitOcrProcessor @Inject constructor() {
    suspend fun recognize(context: Context, uri: Uri): OcrResultModel {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = recognizer.process(image).await()
            OcrResultModel(text.text, text.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    val box = line.boundingBox
                    OcrTextLine(line.text, box?.top ?: 0, box?.left ?: 0,
                        box?.right ?: 0, box?.bottom ?: 0)
                }
            }.sortedWith(compareBy(OcrTextLine::top, OcrTextLine::left)))
        } finally {
            recognizer.close()
        }
    }
}
