package com.gazai.fuel.ai

import com.gazai.fuel.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class ApiException(val code: Int, message: String) : Exception(message)

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
)

object OpenRouter {
    private const val BASE = "https://openrouter.ai/api/v1"

    @Volatile private var freeCache: List<ModelInfo>? = null

    private fun call(path: String, apiKey: String?, body: String?): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Title", "GazAI")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/mcastae813/gazai")
            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, text.take(300))
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** Free models currently offered by OpenRouter (prompt + completion price both 0). */
    suspend fun freeModels(force: Boolean = false): List<ModelInfo> = withContext(Dispatchers.IO) {
        freeCache?.takeIf { !force }?.let { return@withContext it }
        val data = JSONObject(call("/models", null, null)).getJSONArray("data")
        val out = ArrayList<ModelInfo>()
        for (i in 0 until data.length()) {
            val m = data.getJSONObject(i)
            val p = m.optJSONObject("pricing") ?: continue
            if (p.optString("prompt") != "0" || p.optString("completion") != "0") continue
            val arch = m.optJSONObject("architecture")
            val inputs = arch?.optJSONArray("input_modalities")
            val outputs = arch?.optJSONArray("output_modalities")
            val outText = outputs == null || outputs.contains("text")
            if (!outText) continue
            val image = inputs?.contains("image")
                ?: (arch?.optString("modality").orEmpty().substringBefore("->").contains("image"))
            out += ModelInfo(m.getString("id"), image)
        }
        freeCache = out
        out
    }

    private fun JSONArray.contains(s: String): Boolean = (0 until length()).any { optString(it) == s }

    private val preference = listOf("gemma-3", "qwen", "llama-3", "mistral", "gemini", "deepseek", "gpt-oss")

    private suspend fun candidates(explicit: String, needImage: Boolean): List<String> {
        val auto = try {
            freeModels()
                .filter { !needImage || it.acceptsImages }
                .filter { !it.id.contains("thinking") && !it.id.contains("r1") }
                .sortedBy { m -> preference.indexOfFirst { m.id.contains(it) }.let { if (it < 0) 99 else it } }
                .map { it.id }
        } catch (e: Exception) {
            if (explicit.isBlank()) throw e else emptyList()
        }
        return (listOfNotNull(explicit.takeIf { it.isNotBlank() }) + auto).distinct().take(5)
    }

    suspend fun parseFillUp(
        apiKey: String,
        explicitModel: String,
        text: String,
        imageDataUri: String?,
        vehicles: List<Vehicle>,
        today: LocalDate,
    ): ParsedFill {
        require(apiKey.isNotBlank()) { "Add your OpenRouter API key in Settings first." }
        val prompt = buildPrompt(text, vehicles, today, imageDataUri != null)
        val content: Any = if (imageDataUri == null) prompt else JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUri)))
        var lastError: Exception? = null
        for (model in candidates(explicitModel, imageDataUri != null)) {
            try {
                val body = JSONObject()
                    .put("model", model)
                    .put("temperature", 0)
                    .put("max_tokens", 500)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .toString()
                val resp = withContext(Dispatchers.IO) { call("/chat/completions", apiKey, body) }
                val msg = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val reply = msg.optString("content")
                return parseReply(reply)
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) throw ApiException(e.code, "OpenRouter rejected the API key.")
                lastError = e // 404/429/5xx etc: try the next free model
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No free model available right now.")
    }

    private fun buildPrompt(text: String, vehicles: List<Vehicle>, today: LocalDate, hasImage: Boolean): String {
        val v = vehicles.joinToString("; ") { "${it.name} (${it.type})" }.ifBlank { "none" }
        return """
You extract fuel fill-up records. ${if (hasImage) "The image is a photo of a fuel pump display or receipt. On a pump, 'Total', 'Sale', 'Amount' or 'Price' is total_cost and 'Volume', 'Gallons' or 'Liters' is volume; the small per-unit number is price_per_unit. " else ""}
User vehicles: $v.
Today is $today.
User note: ${text.ifBlank { "(none)" }}

Reply with ONLY one JSON object, no markdown, with these keys (use null when unknown, never guess numbers):
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
Numbers must be plain numbers (use '.' as decimal separator, no currency symbols).
""".trim()
    }

    internal fun parseReply(reply: String): ParsedFill {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("Model did not return JSON.")
        val j = JSONObject(reply.substring(start, end + 1))
        fun num(k: String): Double? {
            if (j.isNull(k) || !j.has(k)) return null
            val o = j.get(k)
            return when (o) {
                is Number -> o.toDouble()
                is String -> o.replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                else -> null
            }
        }
        fun str(k: String): String? = if (j.isNull(k) || !j.has(k)) null else j.optString(k).takeIf { it.isNotBlank() && it != "null" }
        val full = if (j.isNull("full_tank") || !j.has("full_tank")) null else j.optBoolean("full_tank")
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
