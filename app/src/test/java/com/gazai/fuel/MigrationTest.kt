package com.gazai.fuel

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.gazai.fuel.data.AppDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds databases exactly like the released app versions created them, then opens them with the
 * current Room schema. Room validates every table after migrating, so a wrong migration fails here
 * instead of crashing on users' phones after an update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private val v1Fuel = "CREATE TABLE IF NOT EXISTS `fuel_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`vehicleId` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `odometerKm` REAL, `liters` REAL NOT NULL, " +
        "`totalCost` REAL NOT NULL, `fullTank` INTEGER NOT NULL, `station` TEXT, `source` TEXT NOT NULL, " +
        "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    private val v1Index = "CREATE INDEX IF NOT EXISTS `index_fuel_entries_vehicleId` ON `fuel_entries` (`vehicleId`)"

    @Before @After
    fun clean() {
        ctx.deleteDatabase("gazai.db")
    }

    private fun createRaw(version: Int, vararg sql: String) {
        val file = ctx.getDatabasePath("gazai.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            sql.forEach(db::execSQL)
            db.version = version
        }
    }

    private fun openAndRead(): List<String> {
        val db = AppDb.create(ctx)
        try {
            val out = ArrayList<String>()
            db.openHelper.readableDatabase.query(
                "SELECT v.name, v.make, COUNT(f.id) FROM vehicles v LEFT JOIN fuel_entries f ON f.vehicleId = v.id GROUP BY v.id ORDER BY v.id",
            ).use { c ->
                while (c.moveToNext()) out += "${c.getString(0)}|${c.getString(1)}|${c.getInt(2)}"
            }
            return out
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFromVersion1() {
        createRaw(
            1,
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL)",
            v1Fuel, v1Index,
            "INSERT INTO vehicles (id, name, type) VALUES (1, 'Bike', 'motorcycle')",
            "INSERT INTO fuel_entries (vehicleId, dateMillis, odometerKm, liters, totalCost, fullTank, station, source) VALUES (1, 0, 100.0, 8.0, 16.0, 1, NULL, 'ai')",
        )
        assertEquals(listOf("Bike|null|1"), openAndRead())
    }

    @Test
    fun migratesFromVersion2() {
        createRaw(
            2,
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `photoPath` TEXT)",
            v1Fuel, v1Index,
            "CREATE TABLE IF NOT EXISTS `maintenance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, " +
                "`dateMillis` INTEGER NOT NULL, `odometerKm` REAL, `type` TEXT NOT NULL, `cost` REAL NOT NULL, `notes` TEXT, `nextDueKm` REAL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_maintenance_vehicleId` ON `maintenance` (`vehicleId`)",
            "INSERT INTO vehicles (id, name, type, photoPath) VALUES (1, 'Car', 'car', NULL)",
            "INSERT INTO maintenance (vehicleId, dateMillis, odometerKm, type, cost, notes, nextDueKm) VALUES (1, 0, 5000.0, 'Oil change', 40.0, NULL, 10000.0)",
        )
        assertEquals(listOf("Car|null|0"), openAndRead())
    }

    @Test
    fun freshInstallOpens() {
        assertEquals(emptyList<String>(), openAndRead())
    }
}
