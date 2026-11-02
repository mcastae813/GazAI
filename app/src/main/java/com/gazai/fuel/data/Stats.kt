package com.gazai.fuel.data

object Units {
    const val KM_PER_MI = 1.609344
    const val L_PER_GAL = 3.785411784
    const val MPG_FACTOR = 235.214583 // mpg = 235.2146 / (L/100km)
}

data class Summary(
    val fills: Int,
    val totalCost: Double,
    val totalLiters: Double,
    /** liters per 100 km averaged over measurable full-tank intervals */
    val avgLPer100: Double?,
    val lastPricePerLiter: Double?,
)

/**
 * Full-tank-to-full-tank consumption (L/100km) per entry id.
 * An entry gets a value when it is a full fill-up, has an odometer, and a previous
 * full fill-up with an odometer exists; partial fills in between are added to the fuel used.
 */
fun consumptionByEntry(entries: List<FuelEntry>): Map<Long, Double> {
    val out = HashMap<Long, Double>()
    var lastFullOdo: Double? = null
    var accum = 0.0
    for (e in entries.sortedWith(compareBy({ it.dateMillis }, { it.odometerKm ?: 0.0 }, { it.id }))) {
        val prev = lastFullOdo
        if (prev == null) {
            if (e.fullTank && e.odometerKm != null) {
                lastFullOdo = e.odometerKm
                accum = 0.0
            }
            continue
        }
        accum += e.liters
        if (e.fullTank) {
            val odo = e.odometerKm
            if (odo != null) {
                val dist = odo - prev
                if (dist > 0) out[e.id] = accum / dist * 100.0
                lastFullOdo = odo
                accum = 0.0
            } else {
                lastFullOdo = null
                accum = 0.0
            }
        }
    }
    return out
}

fun summarize(entries: List<FuelEntry>): Summary {
    val cons = consumptionByEntry(entries)
    var fuel = 0.0
    var dist = 0.0
    val sorted = entries.sortedWith(compareBy({ it.dateMillis }, { it.odometerKm ?: 0.0 }, { it.id }))
    // Weighted average: sum fuel / sum distance over measured intervals.
    var lastFullOdo: Double? = null
    var accum = 0.0
    for (e in sorted) {
        val prev = lastFullOdo
        if (prev == null) {
            if (e.fullTank && e.odometerKm != null) { lastFullOdo = e.odometerKm; accum = 0.0 }
            continue
        }
        accum += e.liters
        if (e.fullTank) {
            val odo = e.odometerKm
            if (odo != null && cons.containsKey(e.id)) { fuel += accum; dist += odo - prev }
            lastFullOdo = odo
            accum = 0.0
        }
    }
    val last = sorted.lastOrNull()
    return Summary(
        fills = entries.size,
        totalCost = entries.sumOf { it.totalCost },
        totalLiters = entries.sumOf { it.liters },
        avgLPer100 = if (dist > 0) fuel / dist * 100.0 else null,
        lastPricePerLiter = last?.let { if (it.liters > 0) it.totalCost / it.liters else null },
    )
}
