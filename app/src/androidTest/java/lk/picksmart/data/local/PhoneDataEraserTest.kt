package lk.picksmart.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.account.ProfilePhotoCache
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.SyncStateEntity
import lk.picksmart.data.local.entity.SyncTombstoneEntity
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.data.preferences.UserPreferencesRepository
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.ThemeMode
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Deleting the account erases its records and settings on the phone (owner decisions 2026-10-26
 * and 2026-10-27), so a new account made there starts empty. Other accounts on the phone keep
 * their details and photos. Sample data only.
 */
@RunWith(AndroidJUnit4::class)
class PhoneDataEraserTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val root = File(context.cacheDir, "eraser-test").apply { deleteRecursively(); mkdirs() }
    private val db = Room.inMemoryDatabaseBuilder(context, PicksmartDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(root, "prefs.preferences_pb") }),
    )
    private val photoDir = File(root, "profile_photo")
    private val exportDir = File(root, "exports")
    private val eraser = PhoneDataEraser(db, prefs, ProfilePhotoCache(photoDir), exportDir)

    @After
    fun close() {
        db.close()
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun erasesTheRidersRecordsPhotosExportsAndSettingsButKeepsTheThemeAndOthers() = runTest {
        val vehicleId = db.vehicleDao().insert(sampleVehicle().toEntity())
        db.tripDao().insert(sampleTrip(vehicleId).toEntity())
        db.tripDao().insertTombstone(SyncTombstoneEntity(SyncKind.TRIPS, "a".repeat(32), 1))
        db.syncDao().setState(SyncStateEntity("last_backup", "1"))
        prefs.completeOnboarding(defaultVehicleId = vehicleId)
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setMobileNumber("rider-1", "077 123 4567")
        prefs.setMobileNumber("rider-2", "071 000 0000")
        photoDir.mkdirs()
        File(photoDir, "rider-1.own.jpg").writeText("sample")
        File(photoDir, "rider-2.own.jpg").writeText("sample")
        exportDir.mkdirs()
        File(exportDir, "picksmart-trips.csv").writeText("sample")

        eraser.erase("rider-1")

        assertTrue(db.vehicleDao().observeAll().first().isEmpty())
        assertTrue(db.tripDao().observeAll().first().isEmpty())
        assertTrue(db.syncDao().pendingTombstones().isEmpty())
        assertNull(db.syncDao().state("last_backup"))
        assertEquals(0, db.syncDao().observePendingCount().first())
        assertFalse(prefs.onboardingComplete.first())
        assertNull(prefs.defaultVehicleId.first())
        assertNull(prefs.personalDetails("rider-1").first().mobile)
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        assertFalse(File(photoDir, "rider-1.own.jpg").exists())
        assertTrue(File(photoDir, "rider-2.own.jpg").exists())
        assertEquals("071 000 0000", prefs.personalDetails("rider-2").first().mobile)
        assertFalse(exportDir.exists())
    }

    private fun sampleVehicle() = Vehicle(
        id = 0,
        name = "Sample bike",
        type = VehicleType.MOTORCYCLE,
        fuelType = FuelType.PETROL_92,
        fuelPricePerLitreCents = 30_000,
        fuelPriceSource = FuelPriceSource.MANUAL,
        fuelPriceEffectiveDate = null,
        autoUpdateFuelPrice = false,
        fuelEfficiencyKmPerLitre = 40.0,
        maintenanceReservePerKmCents = 200,
        targetProfitPerKmCents = null,
        isSample = false,
        archived = false,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun sampleTrip(vehicleId: Long) = Trip(
        id = 0,
        vehicleId = vehicleId,
        createdAtMillis = 1,
        updatedAtMillis = 1,
        tripDateTime = Instant.parse("2026-10-21T11:12:00Z"),
        platform = Platform.UBER,
        status = TripStatus.COMPLETED,
        fareCents = 100_000,
        fareMode = FareMode.GROSS_BEFORE_COMMISSION,
        commissionType = CommissionType.PERCENT,
        commissionHundredths = 2_000,
        pickupKm = 2.0,
        tripKm = 8.0,
        returnKm = 0.0,
        otherCostsCents = 0,
        cancellationFeeCents = null,
        notes = "Sample trip",
        rates = TripRates(30_000, 40.0, 200, null),
    )
}
