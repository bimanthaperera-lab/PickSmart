package lk.picksmart.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.migrations.ALL_MIGRATIONS
import lk.picksmart.data.local.migrations.MIGRATION_1_2
import lk.picksmart.data.local.migrations.MIGRATION_2_3
import lk.picksmart.data.local.migrations.MIGRATION_3_4
import lk.picksmart.data.local.migrations.MIGRATION_4_5
import lk.picksmart.data.local.migrations.MIGRATION_5_6
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each migration keeps every existing row and produces exactly the schema Room expects (N7). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PicksmartDatabase::class.java)

    @Test
    fun migrate1To2KeepsVehiclesAndAddsEmptyTripAndIncomeTables() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO vehicles (id, name, type, fuel_type, fuel_price_per_litre_cents, fuel_price_source,
                    fuel_price_effective_date, auto_update_fuel_price, fuel_efficiency_km_per_litre,
                    maintenance_reserve_per_km_cents, target_profit_per_km_cents, is_sample, archived,
                    created_at, updated_at)
                VALUES (7, 'Bajaj RE', 'THREE_WHEELER', 'AUTO_DIESEL', 39200, 'PUBLISHED',
                    '2026-10-01', 1, 37.5, 175, 6000, 1, 0, 1000, 2000)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against 2.json, including indices.
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val vehicle = db.vehicleDao().get(7)!!
            assertEquals("Bajaj RE", vehicle.name)
            assertEquals(39_200L, vehicle.fuelPricePerLitreCents)
            assertEquals("2026-10-01", vehicle.fuelPriceEffectiveDate.toString())
            assertTrue(vehicle.autoUpdateFuelPrice)
            assertEquals(37.5, vehicle.fuelEfficiencyKmPerLitre, 0.0)
            assertEquals(6_000L, vehicle.targetProfitPerKmCents)
            assertTrue(vehicle.isSample)
            assertEquals(2_000L, vehicle.updatedAtMillis)
            assertTrue(db.tripDao().observeAll().first().isEmpty())
            assertTrue(db.otherIncomeDao().observeAll().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate2To3KeepsTripsAndIncomeAndAddsEmptyFuelAndExpenseTables() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO vehicles (id, name, type, fuel_type, fuel_price_per_litre_cents, fuel_price_source,
                    fuel_price_effective_date, auto_update_fuel_price, fuel_efficiency_km_per_litre,
                    maintenance_reserve_per_km_cents, target_profit_per_km_cents, is_sample, archived,
                    created_at, updated_at)
                VALUES (1, 'Honda Dio', 'MOTORCYCLE', 'PETROL_92', 30000, 'MANUAL',
                    NULL, 0, 40.0, 200, NULL, 0, 0, 1000, 1000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO trips (id, vehicle_id, created_at, updated_at, trip_date_time, platform, status,
                    fare_cents, fare_mode, commission_type, commission_hundredths, pickup_km, trip_km, return_km,
                    other_costs_cents, cancellation_fee_cents, notes, fuel_price_per_litre_cents_snapshot,
                    fuel_efficiency_snapshot, maintenance_reserve_per_km_cents_snapshot,
                    target_profit_per_km_cents_snapshot)
                VALUES (3, 1, 1000, 2000, 1792000000000, 'PICKME', 'CANCELLED', 100000, 'GROSS_BEFORE_COMMISSION',
                    'PERCENT', 2000, 2.5, 0.0, 1.0, 0, 15000, 'Rider no-show', 30000, 40.0, 200, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO other_income (id, date, type, platform, amount_cents, note, created_at, updated_at)
                VALUES (4, '2026-10-20', 'BONUS', NULL, 150000, NULL, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against 3.json, including indices.
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            assertEquals("Honda Dio", db.vehicleDao().get(1)!!.name)
            val trip = db.tripDao().get(3)!!
            assertEquals(TripStatus.CANCELLED, trip.status)
            assertEquals(15_000L, trip.cancellationFeeCents)
            assertEquals(2.5, trip.pickupKm, 0.0)
            assertEquals("Rider no-show", trip.notes)
            assertNull(trip.targetProfitPerKmCentsSnapshot)
            val income = db.otherIncomeDao().get(4)!!
            assertEquals(IncomeType.BONUS, income.type)
            assertEquals(150_000L, income.amountCents)
            assertTrue(db.fuelLogDao().observeAll().first().isEmpty())
            assertTrue(db.expenseDao().observeAll().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate3To4KeepsVehiclesAndAddsAnEmptyPlateNumber() = runTest {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                INSERT INTO vehicles (id, name, type, fuel_type, fuel_price_per_litre_cents, fuel_price_source,
                    fuel_price_effective_date, auto_update_fuel_price, fuel_efficiency_km_per_litre,
                    maintenance_reserve_per_km_cents, target_profit_per_km_cents, is_sample, archived,
                    created_at, updated_at)
                VALUES (2, 'Bajaj RE', 'THREE_WHEELER', 'AUTO_DIESEL', 39200, 'PUBLISHED',
                    '2026-10-01', 1, 30.0, 150, 5000, 0, 1, 1000, 3000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO fuel_logs (id, vehicle_id, date, litres, price_per_litre_cents, total_cents, odometer_km,
                    full_tank, created_at, updated_at)
                VALUES (5, 2, '2026-10-21', 4.5, 39200, 176400, 12345.6, 1, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against 4.json.
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val vehicle = db.vehicleDao().get(2)!!
            assertEquals("Bajaj RE", vehicle.name)
            assertEquals(39_200L, vehicle.fuelPricePerLitreCents)
            assertEquals(5_000L, vehicle.targetProfitPerKmCents)
            assertTrue(vehicle.archived)
            assertEquals(3_000L, vehicle.updatedAtMillis)
            assertNull(vehicle.plateNumber)
            assertEquals(176_400L, db.fuelLogDao().get(5)!!.totalCents)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate4To5KeepsEveryRowAndGivesEachADistinctSyncIdToBackUp() = runTest {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO vehicles (id, name, type, fuel_type, fuel_price_per_litre_cents, fuel_price_source,
                    fuel_price_effective_date, auto_update_fuel_price, fuel_efficiency_km_per_litre,
                    maintenance_reserve_per_km_cents, target_profit_per_km_cents, is_sample, archived,
                    created_at, updated_at, plate_number)
                VALUES (1, 'Honda Dio', 'MOTORCYCLE', 'PETROL_92', 41400, 'MANUAL', NULL, 0, 40.0, 200, NULL, 0, 0,
                    1000, 1000, 'WP BCD-1234'),
                    (2, 'Bajaj RE', 'THREE_WHEELER', 'AUTO_DIESEL', 39200, 'PUBLISHED', '2026-10-01', 1, 30.0, 150,
                    5000, 0, 1, 1000, 3000, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO trips (id, vehicle_id, created_at, updated_at, trip_date_time, platform, status,
                    fare_cents, fare_mode, commission_type, commission_hundredths, pickup_km, trip_km, return_km,
                    other_costs_cents, cancellation_fee_cents, notes, fuel_price_per_litre_cents_snapshot,
                    fuel_efficiency_snapshot, maintenance_reserve_per_km_cents_snapshot,
                    target_profit_per_km_cents_snapshot)
                VALUES (3, 1, 5000, 6000, 1761000000000, 'UBER', 'COMPLETED', 100000, 'GROSS_BEFORE_COMMISSION',
                    'PERCENT', 2000, 2.0, 8.0, 0.0, 0, NULL, 'Sample note', 30000, 40.0, 200, NULL)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO other_income (id, date, type, platform, amount_cents, note, created_at, updated_at) " +
                    "VALUES (4, '2026-10-20', 'BONUS', 'PICKME', 150000, NULL, 1, 1)",
            )
            execSQL(
                "INSERT INTO fuel_logs (id, vehicle_id, date, litres, price_per_litre_cents, total_cents, odometer_km, " +
                    "full_tank, created_at, updated_at) VALUES (5, 2, '2026-10-21', 4.5, 39200, 176400, NULL, 1, 1, 1)",
            )
            execSQL(
                "INSERT INTO expenses (id, vehicle_id, date, category, amount_cents, note, odometer_km, created_at, " +
                    "updated_at) VALUES (6, NULL, '2026-10-22', 'PARKING', 10000, NULL, NULL, 1, 1)",
            )
            close()
        }

        // Validates the migrated schema against 5.json, including the unique sync_id indices.
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val vehicles = listOf(db.vehicleDao().get(1)!!, db.vehicleDao().get(2)!!)
            assertEquals("WP BCD-1234", vehicles[0].plateNumber)
            assertEquals(5_000L, vehicles[1].targetProfitPerKmCents)
            assertEquals(3_000L, vehicles[1].updatedAtMillis)
            val trip = db.tripDao().get(3)!!
            assertEquals(100_000L, trip.fareCents)
            assertEquals(6_000L, trip.updatedAtMillis)
            assertEquals("Sample note", trip.notes)
            val income = db.otherIncomeDao().get(4)!!
            val fuel = db.fuelLogDao().get(5)!!
            val expense = db.expenseDao().get(6)!!
            assertEquals(176_400L, fuel.totalCents)
            assertEquals(10_000L, expense.amountCents)

            val syncIds = vehicles.map { it.syncId } + listOf(trip.syncId, income.syncId, fuel.syncId, expense.syncId)
            assertTrue(syncIds.all { it.matches(Regex("[0-9a-f]{32}")) })
            assertEquals(syncIds.size, syncIds.toSet().size)
            // Everything the rider had is waiting to be backed up when they sign in.
            assertEquals(6, db.syncDao().observePendingCount().first())
            assertTrue(db.syncDao().pendingTombstones().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate5To6KeepsTripsAndAddsEmptyLocations() = runTest {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                """
                INSERT INTO trips (id, vehicle_id, created_at, updated_at, trip_date_time, platform, status,
                    fare_cents, fare_mode, commission_type, commission_hundredths, pickup_km, trip_km, return_km,
                    other_costs_cents, cancellation_fee_cents, notes, fuel_price_per_litre_cents_snapshot,
                    fuel_efficiency_snapshot, maintenance_reserve_per_km_cents_snapshot,
                    target_profit_per_km_cents_snapshot, sync_id, sync_pending)
                VALUES (3, 1, 5000, 6000, 1761000000000, 'PICKME', 'COMPLETED', 100000, 'GROSS_BEFORE_COMMISSION',
                    'PERCENT', 2000, 2.0, 8.0, 0.0, 0, NULL, 'Sample note', 30000, 40.0, 200, NULL, 'abc123', 0)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against 6.json.
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val trip = db.tripDao().get(3)!!
            assertEquals(100_000L, trip.fareCents)
            assertEquals("Sample note", trip.notes)
            assertEquals("abc123", trip.syncId)
            assertEquals(6_000L, trip.updatedAtMillis)
            assertNull(trip.pickupLocation)
            assertNull(trip.dropOffLocation)
            // Nothing changed, so nothing is uploaded again.
            assertEquals(0, db.syncDao().observePendingCount().first())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
