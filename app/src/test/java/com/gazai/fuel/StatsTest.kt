package com.gazai.fuel

import com.gazai.fuel.data.FuelEntry
import com.gazai.fuel.data.consumptionByEntry
import com.gazai.fuel.data.summarize
import com.gazai.fuel.data.usageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private fun fill(id: Long, odo: Double?, liters: Double, cost: Double, full: Boolean = true) =
        FuelEntry(id = id, vehicleId = 1, dateMillis = id, odometerKm = odo, liters = liters, totalCost = cost, fullTank = full)

    @Test
    fun consumptionUsesFullTankIntervals() {
        val e = listOf(
            fill(1, 1000.0, 40.0, 80.0),
            fill(2, null, 10.0, 20.0, full = false),
            fill(3, 1500.0, 30.0, 60.0),
        )
        // (10 + 30) L over 500 km = 8 L/100km
        assertEquals(8.0, consumptionByEntry(e)[3L]!!, 1e-9)
        assertEquals(8.0, summarize(e).avgLPer100!!, 1e-9)
    }

    @Test
    fun fullFillWithoutOdometerDoesNotBreakTheInterval() {
        // The usual quick-add case: the middle pump photo was saved without an odometer.
        val e = listOf(
            fill(1, 1000.0, 40.0, 80.0),
            fill(2, null, 30.0, 60.0),
            fill(3, 1600.0, 35.0, 70.0),
        )
        // (30 + 35) L over 600 km
        assertEquals(65.0 / 600.0 * 100.0, consumptionByEntry(e)[3L]!!, 1e-9)
        assertNull(consumptionByEntry(e)[2L])
        assertEquals(65.0 / 600.0 * 100.0, summarize(e).avgLPer100!!, 1e-9)
    }

    @Test
    fun averageIsWeightedOverSeveralIntervals() {
        val e = listOf(
            fill(1, 1000.0, 40.0, 80.0),
            fill(2, 1500.0, 40.0, 80.0), // 8 L/100km
            fill(3, null, 20.0, 40.0),
            fill(4, 2500.0, 40.0, 80.0), // 60 L over 1000 km = 6 L/100km
        )
        val cons = consumptionByEntry(e)
        assertEquals(8.0, cons[2L]!!, 1e-9)
        assertEquals(6.0, cons[4L]!!, 1e-9)
        assertEquals(100.0 / 1500.0 * 100.0, summarize(e).avgLPer100!!, 1e-9)
    }

    @Test
    fun noValueUntilTwoFullFillsWithOdometer() {
        val e = listOf(fill(1, 1000.0, 40.0, 80.0), fill(2, null, 30.0, 60.0))
        assertTrue(consumptionByEntry(e).isEmpty())
        assertNull(summarize(e).avgLPer100)
    }

    @Test
    fun costPerKmIncludesFillsWithoutOdometer() {
        val e = listOf(
            fill(1, 1000.0, 40.0, 80.0), // paid for km before the tracked span
            fill(2, null, 20.0, 40.0),
            fill(3, null, 20.0, 40.0),
            fill(4, 1500.0, 15.0, 30.0),
        )
        val u = usageStats(e)
        assertEquals(500.0, u.distanceKm!!, 1e-9)
        assertEquals((40.0 + 40.0 + 30.0) / 500.0, u.costPerKm!!, 1e-9)
    }

    @Test
    fun costPerKmNeedsTwoReadings() {
        val e = listOf(fill(1, 1000.0, 40.0, 80.0), fill(2, null, 20.0, 40.0))
        assertNull(usageStats(e).costPerKm)
    }
}
