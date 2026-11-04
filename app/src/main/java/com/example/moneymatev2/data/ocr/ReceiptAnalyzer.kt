package com.example.moneymatev2.data.ocr

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.local.CategoryStableId
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.OcrResultModel
import com.example.moneymatev2.domain.model.OcrTextLine
import com.example.moneymatev2.domain.model.ReceiptModel
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ReceiptAnalyzer {
    private data class Label(val phrase: String, val score: Int)
    private data class LabelHit(val label: Label, val end: Int)
    private data class Candidate(val amount: Long, val score: Int)
    private enum class CategoryKind(val stableId: String) {
        FOOD(CategoryStableId.SPEND_FOOD),
        SHOPPING(CategoryStableId.SPEND_SHOPPING),
        HEALTH(CategoryStableId.SPEND_HEALTH),
        TRANSPORT(CategoryStableId.SPEND_TRANSPORT),
        GIFT(CategoryStableId.SPEND_GIFT),
        ENTERTAINMENT(CategoryStableId.SPEND_ENTERTAINMENT)
    }
    private data class Signal(val phrase: String, val weight: Int)

    private val moneyPattern = Regex(
        "(?<![\\p{L}\\d])\\d+(?:[.,]\\d+|[ \\u00a0]\\d{3})*(?:\\s*(?:vn[đĐ]|vnd|[đĐ]|₫|d))?(?![\\p{L}\\d])",
        RegexOption.IGNORE_CASE
    )
    private val currencySuffix = Regex("(?i)\\s*(?:vn[đĐ]|vnd|[đĐ]|₫|d)\\s*$")
    private val datePattern = Regex("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2}|\\d{4})(?!\\d)")
    private val amountLabels = listOf(
        Label("tong thanh toan", 12), Label("so tien phai tra", 12),
        Label("grand total", 12), Label("amount due", 12),
        Label("total payment", 12), Label("amount payable", 12),
        Label("phai tra", 11), Label("tong cong", 10), Label("tong tien", 10),
        Label("thanh toan", 10), Label("total", 9), Label("tong gia tri", 8),
        Label("thanh tien", 6), Label("subtotal", 7), Label("tam tinh", 7)
    )
    private val excludedLabels = listOf(
        "tien khach dua", "khach dua", "tien mat nhan", "tien nhan", "cash received",
        "cash tendered", "cash given", "tien tra lai", "tra lai", "tien thua",
        "tien thoi", "change", "giam gia", "discount", "tien thue", "vat", "tax",
        "don gia", "unit price", "so dien thoai", "dien thoai", "sdt", "phone",
        "ma hoa don", "ma giao dich", "ma don hang", "ma tham chieu", "invoice no",
        "receipt no", "order no", "transaction id"
    ).map { Label(it, -100) }
    private val allLabels = amountLabels + excludedLabels

    private val categorySignals = mapOf(
        CategoryKind.FOOD to listOf(
            Signal("nha hang", 7), Signal("restaurant", 7), Signal("quan an", 7),
            Signal("quan oc", 7), Signal("oc quan", 7),
            Signal("grabfood", 7), Signal("shopeefood", 7),
            Signal("highlands coffee", 7), Signal("highlands", 7),
            Signal("phuc long", 7), Signal("kfc", 7), Signal("lotteria", 7),
            Signal("cafe", 4), Signal("ca phe", 4), Signal("coffee", 4),
            Signal("tra sua", 4), Signal("milk tea", 4),
            Signal("banh mi", 2), Signal("pho", 2), Signal("bun", 2)
        ),
        CategoryKind.SHOPPING to listOf(
            Signal("sieu thi", 7), Signal("supermarket", 7),
            Signal("cua hang tien loi", 7), Signal("convenience store", 7),
            Signal("winmart", 7), Signal("coopmart", 7), Signal("co opmart", 7),
            Signal("bach hoa xanh", 7), Signal("circle k", 7), Signal("grabmart", 7),
            Signal("minimart", 6), Signal("mart", 6), Signal("grocery", 6), Signal("groceries", 6),
            Signal("trung tam thuong mai", 6), Signal("dien may", 6), Signal("electronics", 6),
            Signal("shopping", 4), Signal("shop", 4), Signal("store", 4),
            Signal("quan ao", 3), Signal("giay dep", 3), Signal("phu kien", 3)
        ),
        CategoryKind.TRANSPORT to listOf(
            Signal("grabcar", 7), Signal("grabbike", 7), Signal("grab bike", 7),
            Signal("xanh sm", 7), Signal("tram xang", 7), Signal("gas station", 7),
            Signal("taxi", 5), Signal("xang", 5), Signal("petrol", 5), Signal("gasoline", 5),
            Signal("ve xe", 5), Signal("ve tau", 5), Signal("parking", 5),
            Signal("gui xe", 5), Signal("cau duong", 5), Signal("bus", 5)
        ),
        CategoryKind.HEALTH to listOf(
            Signal("nha thuoc", 7), Signal("pharmacy", 7), Signal("pharmacity", 7),
            Signal("long chau", 7), Signal("benh vien", 7), Signal("hospital", 7),
            Signal("phong kham", 7), Signal("clinic", 7), Signal("nha khoa", 7),
            Signal("dental", 7), Signal("kham benh", 6), Signal("xet nghiem", 6),
            Signal("thuoc", 2), Signal("medicine", 2)
        ),
        CategoryKind.GIFT to listOf(
            Signal("cua hang qua tang", 7), Signal("gift shop", 7), Signal("gift store", 7),
            Signal("qua tang", 6), Signal("goi qua", 6), Signal("gift box", 6),
            Signal("gift wrapping", 6)
        ),
        CategoryKind.ENTERTAINMENT to listOf(
            Signal("rap chieu phim", 7), Signal("cinema", 7), Signal("cgv", 7),
            Signal("galaxy cinema", 7), Signal("ve xem phim", 7), Signal("movie ticket", 7),
            Signal("khu vui choi", 7), Signal("cong vien giai tri", 7),
            Signal("game center", 7), Signal("karaoke", 6), Signal("bowling", 6),
            Signal("concert", 6), Signal("ve su kien", 5)
        )
    )

    fun analyze(
        result: OcrResultModel,
        categories: List<CategoryModel>,
        ownerId: String?,
        trace: ((String) -> Unit)? = null
    ): ReceiptModel {
        val lines = result.lines.ifEmpty {
            result.fullText.lines().mapIndexed { index, line -> OcrTextLine(line, index * 100) }
        }
        val amount = selectTotal(lines, trace)
        val date = findDate(result.fullText)
        // Preserve the existing merchant/note rule; the two new extractors stay independent.
        val merchant = lines.map { it.text.trim() }.firstOrNull { line ->
            line.length in 3..60 && line.any(Char::isLetter) &&
                !line.any(Char::isDigit) && amountLabels.none { containsPhrase(labelWords(line), it.phrase) } &&
                !words(line).contains("hoa don") && !words(line).contains("receipt")
        }?.take(60)
        val categoryId = suggestCategory(result.fullText, categories, ownerId, merchant, trace)
        return ReceiptModel(merchant, amount, date, merchant, categoryId)
    }

    fun findTotal(lines: List<String>): Long? = selectTotal(
        lines.mapIndexed { index, text -> OcrTextLine(text, index * 100) }, null
    )

    fun findTotal(result: OcrResultModel): Long? = selectTotal(
        result.lines.ifEmpty { result.fullText.lines().mapIndexed { index, line -> OcrTextLine(line, index * 100) } },
        null
    )

    private fun selectTotal(lines: List<OcrTextLine>, trace: ((String) -> Unit)?): Long? {
        val ordered = lines.sortedWith(compareBy(OcrTextLine::top, OcrTextLine::left))
        val candidates = mutableListOf<Candidate>()
        for ((index, line) in ordered.withIndex()) {
            for (match in moneyPattern.findAll(line.text)) {
                val raw = match.value
                if (match.range.first > 0 && line.text[match.range.first - 1] == '-') {
                    trace?.invoke("amount rejected: negative value: $raw")
                    continue
                }
                val amount = parseAmount(raw)
                if (amount == null) {
                    trace?.invoke("amount rejected: invalid format or range: $raw")
                    continue
                }
                if (datePattern.findAll(line.text).any { it.range.first <= match.range.last && it.range.last >= match.range.first }) {
                    trace?.invoke("amount rejected: date fragment: $raw")
                    continue
                }
                val hasCurrency = currencySuffix.containsMatchIn(raw)
                val digits = raw.filter(Char::isDigit)
                if (!hasCurrency && digits.length in 9..12 && digits.startsWith('0')) {
                    trace?.invoke("amount rejected: phone-like number: $raw")
                    continue
                }
                val sameLine = lastLabel(line.text.substring(0, match.range.first))
                val nearby = if (sameLine == null) nearbyLabel(ordered, index) else null
                val label = sameLine ?: nearby
                if (label != null && label.score < 0) {
                    trace?.invoke("amount rejected: ${label.phrase}: $raw")
                    continue
                }
                // Nearby labels can belong to headers; never promote a bare item count to an amount.
                if (digits.length < 4 && !hasCurrency) {
                    trace?.invoke("amount rejected: quantity-like number: $raw")
                    continue
                }
                val score = (label?.score ?: 0) + (if (sameLine != null) 2 else 0) +
                    (if (hasCurrency) 1 else 0)
                if (score == 0) {
                    trace?.invoke("amount rejected: no total label or currency: $raw")
                    continue
                }
                candidates += Candidate(amount, score)
                trace?.invoke("amount candidate: $raw -> $amount; score=$score; label=${label?.phrase ?: "currency only"}")
            }
        }
        val bestScore = candidates.maxOfOrNull { it.score } ?: run {
            trace?.invoke("amount result: null; no supported monetary evidence")
            return null
        }
        val bestValues = candidates.filter { it.score == bestScore }.map { it.amount }.distinct()
        val selected = if (bestValues.size == 1 && bestScore >= 5)
            bestValues.single() else null
        trace?.invoke("amount result: ${selected ?: "null"}; best score=$bestScore; tied values=$bestValues")
        return selected
    }

    /** VND is stored as whole dong. Only zero fractional suffixes are safe to discard. */
    fun parseAmount(raw: String): Long? {
        var number = raw.trim().replace('\u00a0', ' ').replace(currencySuffix, "").trim()
        if (!number.matches(Regex("\\d+(?:[., ]\\d+)*"))) return null
        val fraction = Regex("[.,](\\d{2})$").find(number)
        if (fraction != null) {
            if (fraction.groupValues[1] != "00") return null
            number = number.substring(0, fraction.range.first)
        }
        val groups = number.split(Regex("[., ]"))
        if (groups.any { it.isEmpty() }) return null
        if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
        return groups.joinToString("").toLongOrNull()?.takeIf { it > 0 }
    }

    private fun nearbyLabel(lines: List<OcrTextLine>, amountIndex: Int): Label? {
        val amountLine = lines[amountIndex]
        val hits = lines.mapIndexedNotNull { index, labelLine ->
            if (index == amountIndex || moneyPattern.containsMatchIn(labelLine.text)) return@mapIndexedNotNull null
            val label = lastLabel(labelLine.text) ?: return@mapIndexedNotNull null
            val hasBoxes = amountLine.right > amountLine.left && labelLine.right > labelLine.left
            val sameRow = hasBoxes && labelLine.right <= amountLine.left + 12 &&
                min(amountLine.bottom, labelLine.bottom) - max(amountLine.top, labelLine.top) > 0
            val immediatelyAbove = index == amountIndex - 1 && (!hasBoxes ||
                (amountLine.top >= labelLine.bottom &&
                    amountLine.top - labelLine.bottom <= max(50, (labelLine.bottom - labelLine.top) * 2)))
            if (!sameRow && !immediatelyAbove) return@mapIndexedNotNull null
            Triple(label, if (sameRow) 2 else 1, abs(amountLine.top - labelLine.top))
        }
        return hits.sortedWith(compareByDescending<Triple<Label, Int, Int>> { it.second }
            .thenBy { it.third }).firstOrNull()?.first
    }

    private fun lastLabel(text: String): Label? {
        val normalized = labelWords(text)
        return allLabels.flatMap { label ->
            phraseRegex(label.phrase).findAll(normalized).map { LabelHit(label, it.range.last) }.toList()
        }.maxWithOrNull(compareBy<LabelHit> { it.end }.thenBy { it.label.score })?.label
    }

    fun findDate(text: String): Long? {
        for (match in datePattern.findAll(text)) {
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val yearRaw = match.groupValues[3].toInt()
            val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
            try {
                if (year !in 2000..2100) continue
                return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeException) { }
        }
        return null
    }

    fun suggestCategory(text: String, categories: List<CategoryModel>, ownerId: String?): String? =
        suggestCategory(text, categories, ownerId, null, null)

    private fun suggestCategory(
        text: String,
        categories: List<CategoryModel>,
        ownerId: String?,
        merchant: String?,
        trace: ((String) -> Unit)?
    ): String? {
        if (ownerId == null) return null
        val available = categories.filter { it.userId == ownerId && it.type == TransactionType.EXPENSE &&
            !it.isArchived && !it.isMissing }
        val body = words(text)
        val merchantWords = merchant?.let(::words).orEmpty()
        val scores = CategoryKind.entries.associateWith { kind ->
            // One strong business signal must outrank several incidental item names.
            categorySignals.getValue(kind).maxOf { signal ->
                if (hasCategorySignal(body, signal.phrase))
                    signal.weight + if (hasCategorySignal(merchantWords, signal.phrase)) 2 else 0
                else 0
            }
        }
        trace?.invoke("category signals: $scores")
        val ranked = scores.entries.sortedByDescending { it.value }
        val winner = ranked.firstOrNull() ?: return null
        if (winner.value < 2 || winner.value - (ranked.getOrNull(1)?.value ?: 0) < 2) {
            trace?.invoke("category result: null; weak or conflicting signals")
            return null
        }
        val selected = available.filter { it.isDefault && it.stableId == winner.key.stableId }
            .singleOrNull()?.id
        trace?.invoke("category result: ${selected ?: "null"}; group=${winner.key}; available match=${selected != null}")
        return selected
    }

    private fun labelWords(text: String): String = words(text)
        .replace(Regex("\\bt0ng\\b"), "tong")
        .replace(Regex("\\btong c0ng\\b"), "tong cong")
        .replace(Regex("\\bong cong\\b"), "tong cong")
        .replace(Regex("\\bt0tal\\b"), "total")
        .replace(Regex("\\bthanh t0an\\b"), "thanh toan")
        .replace(Regex("\\btienanhtoan\\b"), "thanh toan")

    private fun words(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun phraseRegex(phrase: String) = Regex("(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])")
    private fun containsPhrase(text: String, phrase: String) = phraseRegex(phrase).containsMatchIn(text)
    private fun hasCategorySignal(text: String, phrase: String): Boolean =
        if (phrase == "pho") Regex("(?<![a-z0-9])pho(?! mai)(?![a-z0-9])").containsMatchIn(text)
        else containsPhrase(text, phrase)
}
