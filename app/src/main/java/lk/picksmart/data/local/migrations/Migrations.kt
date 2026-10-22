package lk.picksmart.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 adds the `trips` and `other_income` tables. The vehicles table is untouched. The SQL
 * is copied from the exported `2.json` schema, so Room's identity check matches exactly.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "`trip_date_time` INTEGER NOT NULL, `platform` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`fare_cents` INTEGER NOT NULL, `fare_mode` TEXT NOT NULL, `commission_type` TEXT NOT NULL, " +
                "`commission_hundredths` INTEGER NOT NULL, `pickup_km` REAL NOT NULL, `trip_km` REAL NOT NULL, " +
                "`return_km` REAL NOT NULL, `other_costs_cents` INTEGER NOT NULL, `cancellation_fee_cents` INTEGER, " +
                "`notes` TEXT, `fuel_price_per_litre_cents_snapshot` INTEGER NOT NULL, " +
                "`fuel_efficiency_snapshot` REAL NOT NULL, `maintenance_reserve_per_km_cents_snapshot` INTEGER NOT NULL, " +
                "`target_profit_per_km_cents_snapshot` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_trip_date_time` ON `trips` (`trip_date_time`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_vehicle_id` ON `trips` (`vehicle_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `other_income` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `type` TEXT NOT NULL, `platform` TEXT, `amount_cents` INTEGER NOT NULL, " +
                "`note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_other_income_date` ON `other_income` (`date`)")
    }
}

/**
 * Every schema change after version 1 adds an explicit [Migration] here, plus a
 * `MigrationTestHelper` test. Destructive fallback is never used (CLAUDE.md N7).
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
