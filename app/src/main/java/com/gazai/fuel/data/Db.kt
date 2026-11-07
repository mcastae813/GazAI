package com.gazai.fuel.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Query("SELECT * FROM vehicles ORDER BY id")
    fun vehicles(): Flow<List<Vehicle>>

    /** Current vehicles, read straight from the database (the Flow may not have loaded yet). */
    @Query("SELECT * FROM vehicles ORDER BY id")
    suspend fun vehicleList(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun vehicle(id: Long): Vehicle?

    @Insert
    suspend fun insertVehicle(v: Vehicle): Long

    @Update
    suspend fun updateVehicle(v: Vehicle)

    @Query("UPDATE vehicles SET photoPath = :path WHERE id = :id")
    suspend fun setVehiclePhoto(id: Long, path: String?)

    @Delete
    suspend fun deleteVehicle(v: Vehicle)

    @Query("SELECT * FROM fuel_entries ORDER BY dateMillis DESC, id DESC")
    fun entries(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY dateMillis DESC, id DESC LIMIT 1")
    suspend fun lastEntry(vehicleId: Long): FuelEntry?

    @Query("SELECT * FROM fuel_entries ORDER BY dateMillis DESC, id DESC LIMIT 1")
    suspend fun lastEntryAnyVehicle(): FuelEntry?

    @Insert
    suspend fun insertEntry(e: FuelEntry): Long

    @Update
    suspend fun updateEntry(e: FuelEntry)

    @Delete
    suspend fun deleteEntry(e: FuelEntry)

    @Query("SELECT * FROM maintenance ORDER BY dateMillis DESC, id DESC")
    fun maintenance(): Flow<List<MaintenanceRecord>>

    @Insert
    suspend fun insertMaintenance(m: MaintenanceRecord): Long

    @Delete
    suspend fun deleteMaintenance(m: MaintenanceRecord)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `photoPath` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `maintenance` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, " +
                "`dateMillis` INTEGER NOT NULL, " +
                "`odometerKm` REAL, " +
                "`type` TEXT NOT NULL, " +
                "`cost` REAL NOT NULL, " +
                "`notes` TEXT, " +
                "`nextDueKm` REAL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_vehicleId` ON `maintenance` (`vehicleId`)")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `make` TEXT")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `model` TEXT")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `year` INTEGER")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `plate` TEXT")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fuelType` TEXT")
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `tankLiters` REAL")
    }
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

@Database(
    entities = [Vehicle::class, FuelEntry::class, MaintenanceRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FuelDao

    companion object {
        @Volatile private var instance: AppDb? = null

        /**
         * The app-wide database. There must be only one: Room refreshes Flows only for writes made through
         * the same instance, and sharing a photo to GazAI opens a second activity (and ViewModel).
         */
        fun get(ctx: Context): AppDb =
            instance ?: synchronized(this) { instance ?: create(ctx).also { instance = it } }

        /** A new, separate instance. Use [get] in the app; this is for tests. */
        fun create(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "gazai.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
