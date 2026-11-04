package com.example.moneymatev2.data.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local-only capture: samples are pushed to app external files, never bundled in an APK. */
@RunWith(AndroidJUnit4::class)
class ReceiptSampleCaptureTest {
    @Test fun captureActualMlKitOutput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ocr-fixtures")
        assumeTrue("Push the local receipt samples to $directory first", directory.isDirectory)
        val entries = JSONArray()
        val categories = listOf("spend_food", "spend_shopping", "spend_transport", "spend_health")
            .map { stableId -> CategoryModel("fixture", stableId, stableId, stableId,
                TransactionType.EXPENSE, "ic_category", "#000000", true, false) }
        for (index in 1..5) {
            val file = File(directory, "receipt_%02d.jpg".format(index))
            assumeTrue("Missing local sample ${file.name}", file.isFile)
            val result = MlKitOcrProcessor().recognize(context, Uri.fromFile(file))
            assertTrue("No ML Kit text for ${file.name}", result.fullText.isNotBlank())
            val lines = JSONArray()
            result.lines.forEach { line ->
                lines.put(JSONObject().put("text", line.text).put("top", line.top)
                    .put("left", line.left).put("right", line.right).put("bottom", line.bottom))
            }
            val trace = JSONArray()
            val receipt = ReceiptAnalyzer().analyze(result, categories, "fixture") { trace.put(it) }
            entries.put(JSONObject().put("image", file.name).put("fullText", result.fullText)
                .put("lines", lines).put("analyzedAmount", receipt.totalAmount)
                .put("analyzedDate", receipt.transactionDate)
                .put("analyzedCategory", receipt.suggestedCategoryId).put("analysisTrace", trace))
        }
        File(directory, "mlkit-capture.json").writeText(entries.toString(2))
    }
}
