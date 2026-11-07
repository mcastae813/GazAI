package com.gazai.fuel.data

import android.content.Context

data class Settings(
    val apiKey: String = "",
    /** Empty = pick a free model automatically. */
    val textModel: String = "",
    val visionModel: String = "",
    val imperial: Boolean = false,
    val currency: String = "$",
    /** Save AI results immediately when they look complete. */
    val autoSave: Boolean = true,
    /** "kml" (km/L), "l100" (L/100km) or "mpg" */
    val effUnit: String = "kml",
    /** Vehicle id used for quick-add; -1 = first vehicle. */
    val defaultVehicleId: Long = -1,
    /** Open the camera as soon as the app starts. */
    val openCameraOnStart: Boolean = true,
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gazai", Context.MODE_PRIVATE)

    /** The API key has its own file so Android backups can leave it out (see the backup rules in res/xml). */
    private val keyStore = ctx.getSharedPreferences("gazai_key", Context.MODE_PRIVATE)

    init {
        // 0.3 and earlier kept the key with the other settings. Drop that copy only once the move is saved.
        sp.getString("apiKey", null)?.let { old ->
            val moved = !keyStore.getString("apiKey", null).isNullOrBlank() ||
                keyStore.edit().putString("apiKey", old).commit()
            if (moved) sp.edit().remove("apiKey").apply()
        }
    }

    fun load() = Settings(
        apiKey = keyStore.getString("apiKey", "") ?: "",
        textModel = sp.getString("textModel", "") ?: "",
        visionModel = sp.getString("visionModel", "") ?: "",
        imperial = sp.getBoolean("imperial", false),
        currency = sp.getString("currency", "$") ?: "$",
        autoSave = sp.getBoolean("autoSave", true),
        effUnit = sp.getString("effUnit", "kml") ?: "kml",
        defaultVehicleId = sp.getLong("defaultVehicleId", -1),
        openCameraOnStart = sp.getBoolean("openCameraOnStart", true),
    )

    fun save(s: Settings) {
        keyStore.edit().putString("apiKey", s.apiKey.trim()).apply()
        sp.edit()
            .putString("textModel", s.textModel.trim())
            .putString("visionModel", s.visionModel.trim())
            .putBoolean("imperial", s.imperial)
            .putString("currency", s.currency)
            .putBoolean("autoSave", s.autoSave)
            .putString("effUnit", s.effUnit)
            .putLong("defaultVehicleId", s.defaultVehicleId)
            .putBoolean("openCameraOnStart", s.openCameraOnStart)
            .apply()
    }
}
