package lk.picksmart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import lk.picksmart.data.local.dao.ExpenseDao
import lk.picksmart.data.local.dao.FuelLogDao
import lk.picksmart.data.local.dao.OtherIncomeDao
import lk.picksmart.data.local.dao.TripDao
import lk.picksmart.data.local.dao.VehicleDao
import lk.picksmart.data.local.entity.ExpenseEntity
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
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PicksmartDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun tripDao(): TripDao

    abstract fun otherIncomeDao(): OtherIncomeDao

    abstract fun fuelLogDao(): FuelLogDao

    abstract fun expenseDao(): ExpenseDao

    companion object {
        /** The file Auto Backup includes (`database` domain). Never rename it. */
        const val NAME = "picksmart.db"

        /**
         * Opens the app database. Schema changes go through [ALL_MIGRATIONS]; there is
         * deliberately no destructive fallback in any build type (CLAUDE.md N7).
         */
        fun build(context: Context): PicksmartDatabase =
            Room.databaseBuilder(context.applicationContext, PicksmartDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
