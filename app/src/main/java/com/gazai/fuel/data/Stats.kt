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

/** Oldest first; on the same day, readings without an odometer go first. */
private val chronological = compareBy<FuelEntry>({ it.dateMillis }, { it.odometerKm ?: 0.0 }, { it.id })

/** Fuel burned over a measured distance, closed by the fill-up [closingId]. */
private class Interval(val closingId: Long, val liters: Double, val km: Double)

/**
 * Full-tank-to-full-tank intervals. The tank is full after every full fill-up, so all fuel added after a
 * full fill-up with an odometer, up to and including the next full fill-up with an odometer, was burned
 * over that distance. Fill-ups without an odometer in between (full or partial) just add their fuel.
 */
private fun intervals(entries: List<FuelEntry>): List<Interval> {
    val out = ArrayList<Interval>()
    var lastOdo: Double? = null
    var accum = 0.0
    for (e in entries.sortedWith(chronological)) {
        val odo = e.odometerKm
        val prev = lastOdo
        if (prev == null) {
            if (e.fullTank && odo != null) {
                lastOdo = odo
                accum = 0.0
            }
            continue
        }
        accum += e.liters
        if (e.fullTank && odo != null) {
            val dist = odo - prev
            if (dist > 0) out += Interval(e.id, accum, dist)
            lastOdo = odo
            accum = 0.0
        }
    }
    return out
}

/** Full-tank-to-full-tank consumption (L/100km), keyed by the fill-up that closes each interval. */
fun consumptionByEntry(entries: List<FuelEntry>): Map<Long, Double> =
    intervals(entries).associate { it.closingId to it.liters / it.km * 100.0 }

fun summarize(entries: List<FuelEntry>): Summary {
    // Weighted average: total fuel / total distance over the measured intervals.
    val measured = intervals(entries)
    val fuel = measured.sumOf { it.liters }
    val dist = measured.sumOf { it.km }
    val last = entries.sortedWith(chronological).lastOrNull()
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
        .sortedWith(chronological)
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

/** Distance between the first and last odometer readings, and the fuel cost per km over that span. */
fun usageStats(entries: List<FuelEntry>): UsageStats {
    val sorted = entries.sortedWith(chronological)
    val first = sorted.indexOfFirst { it.odometerKm != null }
    val last = sorted.indexOfLast { it.odometerKm != null }
    if (first < 0 || last <= first) return UsageStats(null, null)
    val dist = sorted[last].odometerKm!! - sorted[first].odometerKm!!
    if (dist <= 0) return UsageStats(null, null)
    // The fill at the first reading paid for km driven before the span; every later fill up to the last
    // reading paid for km inside it, whether or not it has an odometer.
    val cost = sorted.subList(first + 1, last + 1).sumOf { it.totalCost }
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
