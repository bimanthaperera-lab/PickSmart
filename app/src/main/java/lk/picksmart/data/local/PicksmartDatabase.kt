package lk.picksmart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import lk.picksmart.data.local.dao.AlertDao
import lk.picksmart.data.local.dao.ExpenseDao
import lk.picksmart.data.local.dao.FuelLogDao
import lk.picksmart.data.local.dao.OtherIncomeDao
import lk.picksmart.data.local.dao.TripDao
import lk.picksmart.data.local.dao.VehicleDao
import lk.picksmart.data.local.dao.SyncDao
import lk.picksmart.data.local.entity.AlertEntity
import lk.picksmart.data.local.entity.ExpenseEntity
import lk.picksmart.data.local.entity.SyncStateEntity
import lk.picksmart.data.local.entity.SyncTombstoneEntity
import lk.picksmart.data.local.entity.FuelLogEntity
import lk.picksmart.data.local.entity.OtherIncomeEntity
import lk.picksmart.data.local.entity.TripEntity
import lk.picksmart.data.local.entity.VehicleEntity
import lk.picksmart.data.local.migrations.ALL_MIGRATIONS

@Database(
    entities = [
        VehicleEntity::class,
        TripEntity::class,
        OtherIncomeEntity::class,
        FuelLogEntity::class,
        ExpenseEntity::class,
        SyncTombstoneEntity::class,
        SyncStateEntity::class,
        AlertEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PicksmartDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun tripDao(): TripDao

    abstract fun otherIncomeDao(): OtherIncomeDao

    abstract fun fuelLogDao(): FuelLogDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun syncDao(): SyncDao

    abstract fun alertDao(): AlertDao

    companion object {
        /**
         * Opens the database file [name] (`databaseName` in `DataSpaces.kt`; the first one is
         * `picksmart.db`, never renamed). Auto Backup includes them all (`database` domain).
         * Schema changes go through [ALL_MIGRATIONS]; there is deliberately no destructive
         * fallback in any build type (CLAUDE.md N7).
         */
        fun build(context: Context, name: String): PicksmartDatabase =
            Room.databaseBuilder(context.applicationContext, PicksmartDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
