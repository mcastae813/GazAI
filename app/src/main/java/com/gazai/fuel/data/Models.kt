package com.gazai.fuel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "car" or "motorcycle" */
    val type: String,
    /** Absolute path of a photo copied into app storage, or null. */
    val photoPath: String? = null,
)

/** All values are stored in metric: liters, kilometers. */
@Entity(
    tableName = "fuel_entries",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateMillis: Long,
    val odometerKm: Double?,
    val liters: Double,
    val totalCost: Double,
    val fullTank: Boolean = true,
    val station: String? = null,
    /** "manual" or "ai" */
    val source: String = "manual",
)

/** A service/maintenance record. Distances are in km. */
@Entity(
    tableName = "maintenance",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class MaintenanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateMillis: Long,
    val odometerKm: Double?,
    val type: String,
    val cost: Double,
    val notes: String? = null,
    /** Odometer (km) at which this service is next due, if known. */
    val nextDueKm: Double? = null,
)
