package com.gazai.fuel.ai

import com.gazai.fuel.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate

class ApiException(val code: Int, message: String) : Exception(message)

/** Shown to the user as-is: says why reading failed and what each model answered. */
class ReadFailure(message: String) : Exception(message)

data class ModelInfo(val id: String, val acceptsImages: Boolean)

/** Structured result of reading a fill-up from text and/or a photo. Units are as the model saw them. */
data class ParsedFill(
    val vehicle: String?,
    val date: LocalDate?,
    val odometer: Double?,
    val odometerUnit: String?, // "km" | "mi"
    val volume: Double?,
    val volumeUnit: String?, // "L" | "gal"
    val totalCost: Double?,
    val pricePerUnit: Double?,
    val fullTank: Boolean?,
    val station: String?,
) {
    private val known get() = listOf(volume, totalCost, pricePerUnit).count { it != null }

    /** Enough to work out both liters and cost. */
    val isComplete get() = known >= 2

    val score get() = known
}

data class ReadResult(val fill: ParsedFill, val model: String)

object OpenRouter {
    private const val BASE = "https://openrouter.ai/api/v1"

    @Volatile private var freeCache: List<ModelInfo>? = null

    private fun call(path: String, apiKey: String?, body: String?): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Title", "GazAI")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/mcastae813/gazai")
            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, errorMessage(text))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull() ?: body.take(200)

    private fun isZero(s: String?): Boolean = s != null && s.toDoubleOrNull() == 0.0

    /** Free models currently offered by OpenRouter (prompt + completion price both 0). */
    suspend fun freeModels(force: Boolean = false): List<ModelInfo> = withContext(Dispatchers.IO) {
        freeCache?.takeIf { !force }?.let { return@withContext it }
        val data = JSONObject(call("/models", null, null)).getJSONArray("data")
        val out = ArrayList<ModelInfo>()
        for (i in 0 until data.length()) {
            val m = data.getJSONObject(i)
            val p = m.optJSONObject("pricing") ?: continue
            if (!isZero(p.optString("prompt")) || !isZero(p.optString("completion"))) continue
            val id = m.getString("id")
            if (id.contains("guard") || id.contains("embed")) continue
            val arch = m.optJSONObject("architecture")
            val inputs = arch?.optJSONArray("input_modalities")
            val outputs = arch?.optJSONArray("output_modalities")
            if (outputs != null && !outputs.has("text")) continue
            val image = inputs?.has("image")
                ?: arch?.optString("modality").orEmpty().substringBefore("->").contains("image")
            out += ModelInfo(id, image)
        }
        freeCache = out
        out
    }

    private fun JSONArray.has(s: String): Boolean = (0 until length()).any { optString(it) == s }

    private val visionPreference = listOf("qwen3-vl", "qwen2.5-vl", "-vl", "gemma-3", "llama-4", "mistral-small", "gemini", "pixtral")
    private val textPreference = listOf("llama-3.3", "gemma-3", "qwen3", "mistral", "deepseek-chat", "gpt-oss", "llama")

    private fun rank(id: String, preference: List<String>): Int {
        val p = preference.indexOfFirst { id.contains(it) }.let { if (it < 0) 50 else it }
        // Reasoning-only models are slow and often run out of tokens before answering.
        return p + if (id.contains("thinking") || id.contains("r1")) 100 else 0
    }

    private suspend fun candidates(explicit: String, needImage: Boolean, limit: Int): List<String> {
        val auto = try {
            val pref = if (needImage) visionPreference else textPreference
            freeModels().filter { !needImage || it.acceptsImages }.map { it.id }.sortedBy { rank(it, pref) }
        } catch (e: Exception) {
            if (explicit.isBlank()) throw ReadFailure("Can't reach OpenRouter (${explain(e)}). Check your internet connection.")
            emptyList()
        }
        return (listOfNotNull(explicit.trim().takeIf { it.isNotBlank() }) + auto).distinct().take(limit)
    }

    /** One chat completion; returns the text that should contain the JSON. */
    private suspend fun complete(apiKey: String, model: String, content: Any): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("max_tokens", 1500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .toString()
        val root = JSONObject(withContext(Dispatchers.IO) { call("/chat/completions", apiKey, body) })
        root.optJSONObject("error")?.let { throw ApiException(it.optInt("code", 502), it.optString("message")) }
        val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("no answer")
        val text = when (val c = msg.opt("content")) {
            is String -> c
            is JSONArray -> (0 until c.length()).joinToString("\n") { c.optJSONObject(it)?.optString("text").orEmpty() }
            else -> ""
        }
        if (text.contains('{')) return text
        // Some reasoning models put everything in the reasoning field.
        val reasoning = msg.optString("reasoning")
        if (reasoning.contains('{')) return reasoning
        throw IllegalStateException("empty answer")
    }

    internal fun explain(e: Exception): String = when (e) {
        is ApiException -> when {
            e.code == 429 -> "busy / rate limited (free models allow about 50 requests a day without credits)"
            e.code == 404 && (e.message.orEmpty().contains("data policy", true) || e.message.orEmpty().contains("privacy", true)) ->
                "blocked by your OpenRouter privacy settings"
            e.code == 404 -> "not available right now"
            e.code == 402 -> "needs credits"
            e.code == 408 || e.code >= 500 -> "provider error, try again"
            else -> "error ${e.code}: ${e.message.orEmpty().take(80)}"
        }
        is SocketTimeoutException -> "timed out"
        is UnknownHostException -> "no internet connection"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    /**
     * Reads a fill-up from a note and/or a photo. Photo: tries free vision models (with the on-device OCR
     * text as a hint), then falls back to text models fed only the OCR text. Keeps trying while models
     * answer without the numbers; throws [ReadFailure] listing what each model did.
     */
    suspend fun parseFillUp(
        apiKey: String,
        visionModel: String,
        textModel: String,
        text: String,
        imageDataUri: String?,
        ocrText: String?,
        vehicles: List<Vehicle>,
        today: LocalDate,
    ): ReadResult {
        if (apiKey.isBlank()) throw ReadFailure("Add your free OpenRouter API key in Settings first.")
        val attempts = mutableListOf<String>()
        var best: ReadResult? = null
        val ocr = ocrText?.trim()?.take(1500)?.takeIf { it.isNotEmpty() }

        suspend fun tryModels(models: List<String>, content: Any): ReadResult? {
            for (m in models) {
                try {
                    val fill = parseReply(complete(apiKey, m, content))
                    val r = ReadResult(fill, m)
                    if (fill.isComplete) return r
                    if (fill.score > (best?.fill?.score ?: 0)) best = r
                    attempts += "$m: ${if (fill.score > 0) "read only part of it" else "couldn't find the numbers"}"
                } catch (e: ApiException) {
                    if (e.code == 401) throw ReadFailure("OpenRouter rejected the API key. Check it in Settings.")
                    attempts += "$m: ${explain(e)}"
                } catch (e: Exception) {
                    attempts += "$m: ${explain(e)}"
                }
            }
            return null
        }

        if (imageDataUri != null) {
            val prompt = buildPrompt(text, vehicles, today, if (ocr != null) Mode.PhotoWithOcr(ocr) else Mode.Photo)
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUri)))
            val vision = candidates(visionModel, needImage = true, limit = 4)
            if (vision.isEmpty()) attempts += "No free photo model is available on OpenRouter right now."
            tryModels(vision, content)?.let { return it }
            if (ocr != null) {
                tryModels(candidates(textModel, needImage = false, limit = 3), buildPrompt(text, vehicles, today, Mode.OcrOnly(ocr)))
                    ?.let { return it }
            } else {
                attempts += "On-device text recognition found no text in the photo."
            }
        } else {
            tryModels(candidates(textModel, needImage = false, limit = 4), buildPrompt(text, vehicles, today, Mode.Text))
                ?.let { return it }
        }
        best?.let { return it }

        val headline = when {
            attempts.any { it.contains("privacy settings") } ->
                "Your OpenRouter privacy settings block free models. Open openrouter.ai/settings/privacy and allow free model endpoints, then try again."
            attempts.isNotEmpty() && attempts.all { it.contains("rate limited") } ->
                "All free models are busy or your daily free limit is used up. Try again in a while."
            imageDataUri != null ->
                "Couldn't read the pump. Make sure the total and liters are sharp and fill the photo, or type them in."
            else -> "Couldn't read the fill-up."
        }
        throw ReadFailure(headline + "\n\nDetails:\n" + attempts.joinToString("\n") { "- $it" })
    }

    /** Quick end-to-end check for the Settings screen. */
    suspend fun selfTest(apiKey: String, textModel: String): String {
        val models = try {
            freeModels(force = true)
        } catch (e: Exception) {
            return "Can't reach OpenRouter: ${explain(e)}"
        }
        val head = "${models.size} free models, ${models.count { it.acceptsImages }} accept photos."
        return try {
            val r = parseFillUp(apiKey, "", textModel, "filled up 10 liters for 20.00", null, null, emptyList(), LocalDate.now())
            "$head\nTest OK: ${r.model} read ${r.fill.volume} L for ${r.fill.totalCost}."
        } catch (e: Exception) {
            "$head\n${e.message}"
        }
    }

    private sealed interface Mode {
        data object Text : Mode
        data object Photo : Mode
        data class PhotoWithOcr(val ocr: String) : Mode
        data class OcrOnly(val ocr: String) : Mode
    }

    private fun buildPrompt(text: String, vehicles: List<Vehicle>, today: LocalDate, mode: Mode): String {
        val v = vehicles.joinToString("; ") { veh ->
            veh.name + " (" + listOf(veh.describe(), veh.type).filter { it.isNotBlank() }.joinToString(", ") + ")"
        }.ifBlank { "none" }
        val pumpHint = "On a fuel pump display the largest number labelled 'Total', 'Sale', 'Amount', 'Importe' or " +
            "'$' is total_cost; the one labelled 'Liters', 'Litros', 'Volume' or 'Gallons' is volume; the small one " +
            "labelled per liter/gallon is price_per_unit. Pumps often show volume with 3 decimals."
        val source = when (mode) {
            Mode.Text -> ""
            Mode.Photo -> "The image is a photo of a fuel pump display or receipt. $pumpHint"
            is Mode.PhotoWithOcr -> "The image is a photo of a fuel pump display or receipt. $pumpHint\n" +
                "On-device OCR read this text from the photo (it may contain mistakes; trust the image):\n${mode.ocr}"
            is Mode.OcrOnly -> "You cannot see the photo. On-device OCR read this text from a photo of a fuel pump " +
                "display or receipt (it may contain mistakes):\n${mode.ocr}\n$pumpHint"
        }
        return """
You extract fuel fill-up records.
$source
User vehicles: $v.
Today is $today.
User note: ${text.ifBlank { "(none)" }}

Reply with ONLY one JSON object, no markdown, with these keys (use null when unknown, never invent numbers):
{"vehicle": name of the matching user vehicle or null,
 "date": "YYYY-MM-DD" or null,
 "odometer": number or null,
 "odometer_unit": "km" or "mi" or null,
 "volume": fuel quantity number or null,
 "volume_unit": "L" or "gal" or null,
 "total_cost": number or null,
 "price_per_unit": number or null,
 "full_tank": true or false or null,
 "station": string or null}
Numbers must be plain JSON numbers with '.' as the decimal separator and no currency symbols.
""".trim()
    }

    /**
     * Parses "12.345", "12,345" (decimal comma), "1,234.56", "1.234,56", "$45.20", "45.20 MXN".
     * A single separator is treated as the decimal point because pumps show 3-decimal volumes.
     */
    internal fun parseNumber(raw: String): Double? {
        val s = raw.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (s.none { it.isDigit() }) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val dec = if (lastDot > lastComma) '.' else ','
                val thousands = if (dec == '.') ',' else '.'
                s.replace(thousands.toString(), "").replace(dec, '.')
            }
            lastComma >= 0 -> if (s.count { it == ',' } > 1) s.replace(",", "") else s.replace(',', '.')
            lastDot >= 0 && s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    internal fun parseReply(reply: String): ParsedFill {
        val cleaned = reply.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("answer had no JSON")
        val j = JSONObject(cleaned.substring(start, end + 1))
        fun num(k: String): Double? {
            if (!j.has(k) || j.isNull(k)) return null
            return when (val o = j.get(k)) {
                is Number -> o.toDouble()
                is String -> parseNumber(o)
                else -> null
            }?.takeIf { it > 0 }
        }
        fun str(k: String): String? =
            if (!j.has(k) || j.isNull(k)) null else j.optString(k).trim().takeIf { it.isNotBlank() && it != "null" }
        val full = if (!j.has("full_tank") || j.isNull("full_tank")) null else when (val f = j.get("full_tank")) {
            is Boolean -> f
            is String -> f.equals("true", true)
            else -> null
        }
        return ParsedFill(
            vehicle = str("vehicle"),
            date = str("date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
            odometer = num("odometer"),
            odometerUnit = str("odometer_unit")?.lowercase(),
            volume = num("volume"),
            volumeUnit = str("volume_unit"),
            totalCost = num("total_cost"),
            pricePerUnit = num("price_per_unit"),
            fullTank = full,
            station = str("station"),
        )
    }
}
