package com.gazai.fuel

import com.gazai.fuel.ai.FillRules
import com.gazai.fuel.ai.ParsedFill
import com.gazai.fuel.data.Units
import com.gazai.fuel.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FillRulesTest {
    private fun parsed(
        volume: Double? = null, volumeUnit: String? = null, total: Double? = null, price: Double? = null,
        odometer: Double? = null, odometerUnit: String? = null,
    ) = ParsedFill(
        vehicle = null, date = null, odometer = odometer, odometerUnit = odometerUnit, volume = volume,
        volumeUnit = volumeUnit, totalCost = total, pricePerUnit = price, fullTank = null, station = null,
    )

    // ---- resolve ----

    @Test
    fun litersAreKeptAndGallonsConverted() {
        assertEquals(40.0, FillRules.resolve(parsed(volume = 40.0, volumeUnit = "L", total = 80.0), imperial = true).liters!!, 1e-9)
        assertEquals(40.0, FillRules.resolve(parsed(volume = 40.0, volumeUnit = "litros", total = 80.0), imperial = true).liters!!, 1e-9)
        assertEquals(10 * Units.L_PER_GAL, FillRules.resolve(parsed(volume = 10.0, volumeUnit = "gal", total = 35.0), imperial = false).liters!!, 1e-9)
    }

    @Test
    fun unstatedUnitsAreTheUsersOwn() {
        assertEquals(10 * Units.L_PER_GAL, FillRules.resolve(parsed(volume = 10.0, total = 35.0), imperial = true).liters!!, 1e-9)
        assertEquals(10.0, FillRules.resolve(parsed(volume = 10.0, total = 35.0), imperial = false).liters!!, 1e-9)
        assertEquals(100 * Units.KM_PER_MI, FillRules.resolve(parsed(odometer = 100.0, odometerUnit = "miles"), imperial = false).odometerKm!!, 1e-9)
        assertEquals(100.0, FillRules.resolve(parsed(odometer = 100.0, odometerUnit = "km"), imperial = true).odometerKm!!, 1e-9)
    }

    @Test
    fun missingValueIsWorkedOutFromThePrice() {
        val cost = FillRules.resolve(parsed(volume = 20.0, price = 1.5), imperial = false)
        assertEquals(30.0, cost.totalCost!!, 1e-9)
        val liters = FillRules.resolve(parsed(total = 35.0, price = 3.5, volumeUnit = "gal"), imperial = false)
        assertEquals(10 * Units.L_PER_GAL, liters.liters!!, 1e-9)
    }

    // ---- problem ----

    private fun problem(p: ParsedFill, tank: Double? = null, lastPrice: Double? = null) =
        FillRules.problem(p, FillRules.resolve(p, imperial = false), tank, lastPrice, imperial = false)

    @Test
    fun plausibleReadingHasNoProblem() {
        assertNull(problem(parsed(volume = 32.415, total = 789.5, price = 24.36), tank = 45.0, lastPrice = 24.0))
    }

    @Test
    fun moreThanTheTankHoldsIsFlagged() {
        assertNotNull(problem(parsed(volume = 60.0, total = 120.0), tank = 45.0))
    }

    @Test
    fun numbersThatDontAddUpAreFlagged() {
        // Total and unit price swapped by the model.
        assertNotNull(problem(parsed(volume = 32.415, total = 24.36, price = 789.5)))
    }

    @Test
    fun priceFarFromTheLastFillIsFlagged() {
        // "1,050" misread as 1.05 for 40 L when fuel costs about 24 per liter.
        assertNotNull(problem(parsed(volume = 40.0, total = 1.05), lastPrice = 24.0))
        assertNull(problem(parsed(volume = 40.0, total = 1000.0), lastPrice = 24.0))
    }

    // ---- matchVehicle ----

    private val civic = Vehicle(id = 1, name = "2019 Honda Civic", type = "car", make = "Honda", model = "Civic", year = 2019)
    private val bike = Vehicle(id = 2, name = "2020 Honda CB500F", type = "motorcycle", make = "Honda", model = "CB500F", year = 2020)
    private val oscar = Vehicle(id = 3, name = "Oscar", type = "motorcycle")
    private val garage = listOf(civic, bike)

    @Test
    fun ambiguousNameMatchesNothing() {
        assertNull(FillRules.matchVehicle("Honda", garage))
    }

    @Test
    fun modelOrFullNamePicksTheVehicle() {
        assertEquals(bike, FillRules.matchVehicle("Honda CB500F", garage))
        assertEquals(civic, FillRules.matchVehicle("2019 honda civic", garage))
        assertEquals(civic, FillRules.matchVehicle("the civic", garage))
    }

    @Test
    fun typeWordsPickTheOnlyVehicleOfThatType() {
        assertEquals(bike, FillRules.matchVehicle("my motorbike", garage))
        assertEquals(civic, FillRules.matchVehicle("the car", garage))
        // "car" inside another word doesn't count.
        assertNull(FillRules.matchVehicle("Oscar's", listOf(civic)))
    }

    @Test
    fun nicknameMatches() {
        assertEquals(oscar, FillRules.matchVehicle("Oscar", garage + oscar))
    }
}
