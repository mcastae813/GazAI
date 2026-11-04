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

data class EffPoint(val entry: FuelEntry, val lPer100: Double)

/** Measured consumption points, oldest first. */
fun efficiencySeries(entries: List<FuelEntry>): List<EffPoint> {
    val cons = consumptionByEntry(entries)
    return entries
        .sortedWith(compareBy({ it.dateMillis }, { it.odometerKm ?: 0.0 }, { it.id }))
        .mapNotNull { e -> cons[e.id]?.let { EffPoint(e, it) } }
}

/** Spend per calendar month for the last [months] months ending at [now], oldest first. */
fun monthlySpend(entries: List<FuelEntry>, months: Int, now: java.time.YearMonth): List<Pair<java.time.YearMonth, Double>> {
    val zone = java.time.ZoneId.systemDefault()
    val byMonth = entries.groupBy { java.time.YearMonth.from(java.time.Instant.ofEpochMilli(it.dateMillis).atZone(zone)) }
    return (months - 1 downTo 0).map { back ->
        val ym = now.minusMonths(back.toLong())
        ym to (byMonth[ym]?.sumOf { it.totalCost } ?: 0.0)
    }
}

data class UsageStats(
    val distanceKm: Double?,
    val costPerKm: Double?,
)

/** Distance covered between first and last odometer reading and cost per km over that span. */
fun usageStats(entries: List<FuelEntry>): UsageStats {
    val withOdo = entries.filter { it.odometerKm != null }.sortedBy { it.odometerKm }
    if (withOdo.size < 2) return UsageStats(null, null)
    val dist = withOdo.last().odometerKm!! - withOdo.first().odometerKm!!
    if (dist <= 0) return UsageStats(null, null)
    // The first fill paid for km before the tracked span, so leave it out.
    val cost = withOdo.drop(1).sumOf { it.totalCost }
    return UsageStats(dist, cost / dist)
}

data class DueItem(val record: MaintenanceRecord, val remainingKm: Double)

/** Latest odometer known for a vehicle from fills and services. */
fun latestOdometer(entries: List<FuelEntry>, service: List<MaintenanceRecord>): Double? =
    (entries.mapNotNull { it.odometerKm } + service.mapNotNull { it.odometerKm }).maxOrNull()

/** For each service type, the most recent record that sets a next-due odometer, ordered by urgency. */
fun dueItems(service: List<MaintenanceRecord>, currentOdoKm: Double?): List<DueItem> {
    if (currentOdoKm == null) return emptyList()
    return service
        .groupBy { it.type.trim().lowercase() }
        .values
        .mapNotNull { list -> list.maxByOrNull { it.dateMillis }?.takeIf { it.nextDueKm != null } }
        .map { DueItem(it, it.nextDueKm!! - currentOdoKm) }
        .sortedBy { it.remainingKm }
}
