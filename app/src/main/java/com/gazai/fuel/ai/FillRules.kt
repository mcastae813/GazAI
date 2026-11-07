package com.gazai.fuel.ai

import com.gazai.fuel.data.Units
import com.gazai.fuel.data.Vehicle
import com.gazai.fuel.fmt
import kotlin.math.abs

/** An AI reading in metric, with liters or cost worked out from the unit price when one is missing. */
data class ResolvedFill(
    val odometerKm: Double?,
    val liters: Double?,
    val totalCost: Double?,
)

/** Pure rules for turning an AI reading into a fill-up. No Android, so they are unit tested. */
object FillRules {
    private fun isMiles(unit: String?, imperial: Boolean): Boolean {
        val u = unit?.trim()?.lowercase().orEmpty()
        return when {
            u.startsWith("mi") -> true
            u.startsWith("k") -> false
            else -> imperial
        }
    }

    private fun isGallons(unit: String?, imperial: Boolean): Boolean {
        val u = unit?.trim()?.lowercase().orEmpty()
        return when {
            u.startsWith("gal") -> true
            u.startsWith("l") -> false // L, liters, litres, litros
            else -> imperial
        }
    }

    /** Units the model didn't state are taken to be the user's own. */
    fun resolve(p: ParsedFill, imperial: Boolean): ResolvedFill {
        val odoKm = p.odometer?.let { if (isMiles(p.odometerUnit, imperial)) it * Units.KM_PER_MI else it }
        // price_per_unit is per the same unit as volume, so either one can be worked out from the other two.
        val price = p.pricePerUnit?.takeIf { it > 0 }
        var volume = p.volume
        var total = p.totalCost
        if (total == null && volume != null && price != null) total = volume * price
        if (volume == null && total != null && price != null) volume = total / price
        val liters = volume?.let { if (isGallons(p.volumeUnit, imperial)) it * Units.L_PER_GAL else it }
        return ResolvedFill(odoKm, liters, total)
    }

    /**
     * Why a reading shouldn't be saved without the user checking it, or null when it looks plausible.
     * [lastPricePerLiter] is from the most recent saved fill-up, if any.
     */
    fun problem(
        p: ParsedFill,
        r: ResolvedFill,
        tankLiters: Double?,
        lastPricePerLiter: Double?,
        imperial: Boolean,
    ): String? {
        val liters = r.liters ?: return null
        val total = r.totalCost ?: return null
        if (tankLiters != null && liters > tankLiters * 1.15) {
            val tank = if (imperial) "${fmt(tankLiters / Units.L_PER_GAL, 1)} gal" else "${fmt(tankLiters, 0)} L"
            return "The AI read more fuel than your $tank tank holds."
        }
        val v = p.volume
        val price = p.pricePerUnit
        val t = p.totalCost
        // Pumps round, and some apply discounts, so allow a little slack.
        if (v != null && price != null && t != null && t > 0 && abs(v * price - t) / t > 0.05) {
            return "The numbers don't add up: ${fmt(v, 3)} at ${fmt(price, 3)} isn't ${fmt(t)}."
        }
        if (lastPricePerLiter != null && lastPricePerLiter > 0 && liters > 0) {
            val ratio = total / liters / lastPricePerLiter
            if (ratio < 0.5 || ratio > 2.0) return "That works out to a very different fuel price from your last fill-up."
        }
        return null
    }

    /**
     * The vehicle a typed note refers to, or null when it's unclear. Each rule must match exactly one
     * vehicle; if it matches several, the next, looser rule is tried, so "Honda" with two Hondas gives null.
     */
    fun matchVehicle(name: String, vehicles: List<Vehicle>): Vehicle? {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return null
        fun mentions(part: String?): Boolean {
            val p = part?.trim()?.lowercase().orEmpty()
            return p.length >= 2 && n.contains(p)
        }
        val wantType = when {
            listOf("moto", "bike", "scooter").any { n.contains(it) } -> "motorcycle"
            Regex("\\bcar\\b").containsMatchIn(n) -> "car"
            else -> null
        }
        val rules: List<(Vehicle) -> Boolean> = listOf(
            { it.name.trim().lowercase() == n },
            { mentions(it.model) },
            { v ->
                val vn = v.name.trim().lowercase()
                vn.length >= 2 && (n.contains(vn) || (n.length >= 3 && vn.contains(n)))
            },
            { mentions(it.make) },
            { wantType != null && it.type == wantType },
        )
        for (rule in rules) vehicles.filter(rule).singleOrNull()?.let { return it }
        return null
    }
}
