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

    fun load() = Settings(
        apiKey = sp.getString("apiKey", "") ?: "",
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
        sp.edit()
            .putString("apiKey", s.apiKey.trim())
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
