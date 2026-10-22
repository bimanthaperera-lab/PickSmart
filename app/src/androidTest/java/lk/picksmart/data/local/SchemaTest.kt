package lk.picksmart.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.migrations.ALL_MIGRATIONS
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the "data survives every update" rule (CLAUDE.md N7). A database is created from the
 * committed schema JSON, filled with a row written as raw SQL (as an older app version would have
 * written it), then opened with the app's real builder and migrations. If someone changes an
 * entity without bumping the version and adding a migration, Room's identity check fails here.
 *
 * Each single migration step is validated in [MigrationTest].
 */
@RunWith(AndroidJUnit4::class)
class SchemaTest {

    private val dbName = "schema-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PicksmartDatabase::class.java)

    @Test
    fun version1DatabaseOpensWithTheCurrentAppAndKeepsItsRows() = runTest {
        helper.createDatabase(dbName, 1).apply {
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
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PicksmartDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val vehicle = db.vehicleDao().get(1)!!
            assertEquals("Honda Dio", vehicle.name)
            assertEquals(VehicleType.MOTORCYCLE, vehicle.type)
            assertEquals(FuelType.PETROL_92, vehicle.fuelType)
            assertEquals(30_000L, vehicle.fuelPricePerLitreCents)
            assertEquals(FuelPriceSource.MANUAL, vehicle.fuelPriceSource)
            assertEquals(40.0, vehicle.fuelEfficiencyKmPerLitre, 0.0)
            assertEquals(200L, vehicle.maintenanceReservePerKmCents)
            assertFalse(vehicle.isSample)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
