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
 * Version 3 adds the `fuel_logs` and `expenses` tables. Existing tables are untouched. The SQL is
 * copied from the exported `3.json` schema.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fuel_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicle_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `litres` REAL NOT NULL, " +
                "`price_per_litre_cents` INTEGER NOT NULL, `total_cents` INTEGER NOT NULL, `odometer_km` REAL, " +
                "`full_tank` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_date` ON `fuel_logs` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_vehicle_id` ON `fuel_logs` (`vehicle_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicle_id` INTEGER, `date` TEXT NOT NULL, `category` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, " +
                "`note` TEXT, `odometer_km` REAL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
    }
}

/** Version 4 adds the optional `plate_number` to vehicles. Existing vehicles get NULL. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `plate_number` TEXT")
    }
}

/**
 * Version 5 prepares cloud backup (CLAUDE.md section 14). Every record gets a `sync_id` that is the
 * same on all the rider's devices (existing rows get a random one) and a `sync_pending` flag, set
 * so existing rows are backed up the first time the rider signs in. `sync_tombstones` remembers
 * deletes, and `sync_state` holds the backup's own bookkeeping. No existing value changes.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("vehicles", "trips", "other_income", "fuel_logs", "expenses")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_id` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$table` SET `sync_id` = lower(hex(randomblob(16)))")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_pending` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_sync_id` ON `$table` (`sync_id`)")
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_tombstones` (`kind` TEXT NOT NULL, `sync_id` TEXT NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `sync_pending` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`kind`, `sync_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_state` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

/**
 * Every schema change after version 1 adds an explicit [Migration] here, plus a
 * `MigrationTestHelper` test. Destructive fallback is never used (CLAUDE.md N7).
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
