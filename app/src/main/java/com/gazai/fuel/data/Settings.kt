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
    )

    fun save(s: Settings) {
        sp.edit()
            .putString("apiKey", s.apiKey.trim())
            .putString("textModel", s.textModel.trim())
            .putString("visionModel", s.visionModel.trim())
            .putBoolean("imperial", s.imperial)
            .putString("currency", s.currency)
            .putBoolean("autoSave", s.autoSave)
            .apply()
    }
}
