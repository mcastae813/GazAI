package com.gazai.fuel.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Query("SELECT * FROM vehicles ORDER BY id")
    fun vehicles(): Flow<List<Vehicle>>

    @Insert
    suspend fun insertVehicle(v: Vehicle): Long

    @Delete
    suspend fun deleteVehicle(v: Vehicle)

    @Query("SELECT * FROM fuel_entries ORDER BY dateMillis DESC, id DESC")
    fun entries(): Flow<List<FuelEntry>>

    @Insert
    suspend fun insertEntry(e: FuelEntry): Long

    @Delete
    suspend fun deleteEntry(e: FuelEntry)
}

@Database(entities = [Vehicle::class, FuelEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FuelDao

    companion object {
        fun create(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "gazai.db").build()
    }
}
