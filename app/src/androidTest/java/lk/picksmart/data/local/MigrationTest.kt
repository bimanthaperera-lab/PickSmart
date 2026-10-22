package lk.picksmart.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.migrations.ALL_MIGRATIONS
import lk.picksmart.data.local.migrations.MIGRATION_1_2
import org.junit.Assert.assertEquals
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
}
