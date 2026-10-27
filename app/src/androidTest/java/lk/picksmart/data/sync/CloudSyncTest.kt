package lk.picksmart.data.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import lk.picksmart.data.local.PicksmartDatabase
import lk.picksmart.data.local.entity.SyncKind
import lk.picksmart.data.local.entity.toDomain
import lk.picksmart.data.local.entity.toEntity
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.domain.model.VehicleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Two phones backing up to one in-memory cloud (sample data only). Checks the rules in CLAUDE.md
 * section 14: local data is never replaced by an empty or older cloud copy, the newest edit wins,
 * deletes reach the other phone and stay deleted, and nothing is uploaded twice.
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncTest {

    private val cloud = FakeCloud()
    private lateinit var phoneA: Phone
    private lateinit var phoneB: Phone

    @Before
    fun open() {
        phoneA = Phone(cloud)
        phoneB = Phone(cloud)
    }

    @After
    fun close() {
        phoneA.db.close()
        phoneB.db.close()
    }

    @Test
    fun aNewPhoneGetsEveryRecordWithItsOwnVehicleIds() = runTest {
        // Phone B already has a vehicle of its own, so ids on the two phones differ.
        phoneB.db.vehicleDao().insert(vehicle("Old bike", updatedAt = 1).toEntity())
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.db.fuelLogDao().insert(fuel(vehicleId).toEntity())
        phoneA.db.expenseDao().insert(expense(vehicleId).toEntity())
        phoneA.db.otherIncomeDao().insert(income().toEntity())
        phoneA.settings.mobile = "0771234567"
        phoneA.settings.activeVehicleSyncId = phoneA.db.vehicleDao().get(vehicleId)!!.syncId

        assertEquals(SyncReport(downloaded = 0, uploaded = 5), phoneA.sync(UID))
        val report = phoneB.sync(UID)

        assertEquals(5, report.downloaded)
        // Phone B's own vehicle was backed up too; nothing it had was replaced.
        assertEquals(1, report.uploaded)
        val vehicles = phoneB.db.vehicleDao().observeAll().first()
        assertEquals(listOf("Old bike", "Honda Dio"), vehicles.map { it.name })
        val restored = vehicles.single { it.name == "Honda Dio" }
        val trip = phoneB.db.tripDao().observeAll().first().single()
        assertEquals(restored.id, trip.vehicleId)
        assertNotEquals(vehicleId, restored.id)
        assertEquals(100_000L, trip.fareCents)
        assertEquals(TripRates(30_000, 40.0, 200, null), trip.toDomain().rates)
        assertEquals(restored.id, phoneB.db.fuelLogDao().observeAll().first().single().vehicleId)
        assertEquals(restored.id, phoneB.db.expenseDao().observeAll().first().single().vehicleId)
        assertEquals(150_000L, phoneB.db.otherIncomeDao().observeAll().first().single().amountCents)
        assertEquals("0771234567", phoneB.settings.mobile)
        assertEquals(restored.syncId, phoneB.settings.restoredVehicleSyncId)
        assertEquals(0, phoneA.pending())
        assertEquals(0, phoneB.pending())
    }

    @Test
    fun aRestoredVehicleUpdatesTheSameOneMadeOnThePhone() = runTest {
        val onA = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(onA, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.sync(UID)
        // Phone B made the same bike before signing in, later, with its own efficiency.
        val onB = phoneB.db.vehicleDao().insert(vehicle(" honda  DIO", updatedAt = 5).toEntity().copy(fuelEfficiencyKmPerLitre = 45.0))

        phoneB.sync(UID)

        val bike = phoneB.db.vehicleDao().observeAll().first().single()
        assertEquals(onB, bike.id)
        assertEquals(phoneA.db.vehicleDao().get(onA)!!.syncId, bike.syncId)
        // The phone's edit is newer, so it stays and goes up over the backup's.
        assertEquals(45.0, bike.fuelEfficiencyKmPerLitre, 0.0)
        assertEquals(bike.id, phoneB.db.tripDao().observeAll().first().single().vehicleId)
        assertEquals(2, cloud.count(UID))
        phoneA.sync(UID)
        assertEquals(45.0, phoneA.db.vehicleDao().get(onA)!!.fuelEfficiencyKmPerLitre, 0.0)
    }

    @Test
    fun anEmptyCloudNeverEmptiesThePhone() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(vehicleId, fare = 50_000, updatedAt = 2).toEntity())

        val report = phoneA.sync(UID)

        assertEquals(0, report.downloaded)
        assertEquals(1, phoneA.db.tripDao().observeAll().first().size)
        assertEquals(2, cloud.count(UID))
    }

    @Test
    fun theNewestEditWinsOnBothPhones() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        val tripId = phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.sync(UID)
        phoneB.sync(UID)
        val onB = phoneB.db.tripDao().observeAll().first().single()

        // A edits first, B later; whichever uploads last, B's newer edit must win everywhere.
        val onA = phoneA.db.tripDao().get(tripId)!!
        phoneA.db.tripDao().update(onA.copy(fareCents = 120_000, updatedAtMillis = 10, syncPending = true))
        phoneB.db.tripDao().update(onB.copy(fareCents = 130_000, updatedAtMillis = 20, syncPending = true))
        phoneB.sync(UID)
        phoneA.sync(UID)
        phoneB.sync(UID)

        assertEquals(130_000L, phoneA.db.tripDao().get(tripId)!!.fareCents)
        assertEquals(130_000L, phoneB.db.tripDao().get(onB.id)!!.fareCents)
        assertEquals(0, phoneA.pending())
    }

    @Test
    fun aDeleteReachesTheOtherPhoneAndStaysDeleted() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.sync(UID)
        phoneB.sync(UID)
        val onB = phoneB.db.tripDao().observeAll().first().single()

        phoneB.db.tripDao().deleteAndRemember(onB.id, nowMillis = 30)
        phoneB.sync(UID)
        phoneA.sync(UID)
        // Phone A's next runs must not bring it back, and B must not download it again.
        phoneA.sync(UID)
        phoneB.sync(UID)

        assertTrue(phoneA.db.tripDao().observeAll().first().isEmpty())
        assertTrue(phoneB.db.tripDao().observeAll().first().isEmpty())
        assertEquals(true, cloud.document(UID, SyncKind.TRIPS, onB.syncId)?.get(CloudRecords.DELETED))
        // The deleted record's contents left the cloud with it.
        assertNull(cloud.document(UID, SyncKind.TRIPS, onB.syncId)?.get("fareCents"))
    }

    @Test
    fun anEditNewerThanADeleteKeepsTheRecord() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        val tripId = phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.sync(UID)
        phoneB.sync(UID)
        val onB = phoneB.db.tripDao().observeAll().first().single()

        phoneB.db.tripDao().deleteAndRemember(onB.id, nowMillis = 30)
        val onA = phoneA.db.tripDao().get(tripId)!!
        phoneA.db.tripDao().update(onA.copy(fareCents = 110_000, updatedAtMillis = 40, syncPending = true))
        phoneB.sync(UID)
        phoneA.sync(UID)
        phoneB.sync(UID)

        assertEquals(110_000L, phoneA.db.tripDao().get(tripId)!!.fareCents)
        assertEquals(110_000L, phoneB.db.tripDao().observeAll().first().single().fareCents)
        assertNull(phoneB.db.syncDao().tombstone(SyncKind.TRIPS, onB.syncId))
    }

    @Test
    fun aFailedRunUploadsEverythingNextTime() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        cloud.offline = true

        val failure = runCatching { phoneA.sync(UID) }.exceptionOrNull()

        assertEquals(SyncProblem.OFFLINE, (failure as CloudException).problem)
        assertEquals(2, phoneA.pending())
        cloud.offline = false
        phoneA.sync(UID)
        assertEquals(0, phoneA.pending())
        assertEquals(2, cloud.count(UID))
    }

    @Test
    fun anEditDuringAnUploadStaysPending() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        val tripId = phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        cloud.duringWrite = { kind ->
            if (kind == SyncKind.TRIPS) {
                val trip = phoneA.db.tripDao().get(tripId)!!
                phoneA.db.tripDao().update(trip.copy(fareCents = 90_000, updatedAtMillis = 50, syncPending = true))
                cloud.duringWrite = null
            }
        }

        phoneA.sync(UID)

        assertEquals(1, phoneA.pending())
        phoneA.sync(UID)
        assertEquals(90_000L, (cloud.document(UID, SyncKind.TRIPS, phoneA.db.tripDao().get(tripId)!!.syncId)!!["fareCents"]))
    }

    @Test
    fun anotherRiderSigningInGetsThePhonesRecordsInTheirOwnSpace() = runTest {
        val vehicleId = phoneA.db.vehicleDao().insert(vehicle("Honda Dio", updatedAt = 1).toEntity())
        phoneA.db.tripDao().insert(trip(vehicleId, fare = 100_000, updatedAt = 2).toEntity())
        phoneA.sync(UID)

        val report = phoneA.sync("rider-2")

        assertEquals(2, report.uploaded)
        assertEquals(2, cloud.count("rider-2"))
        assertEquals(2, cloud.count(UID))
    }

    private class Phone(cloud: FakeCloud) {
        val db: PicksmartDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PicksmartDatabase::class.java,
        ).build()
        val settings = FakeSettings()
        private val engine = CloudSync(db.syncDao(), cloud, settings) { block -> db.withTransaction { block() } }
        private var now = 100L

        suspend fun sync(uid: String) = engine.sync(uid, download = true, nowMillis = now++)

        suspend fun pending() = db.syncDao().observePendingCount().first()
    }

    private class FakeSettings : SyncedSettings {
        var mobile: String? = null
        var activeVehicleSyncId: String? = null
        var restoredVehicleSyncId: String? = null

        override suspend fun current(uid: String) = CloudProfile(mobile, activeVehicleSyncId)

        override suspend fun restore(uid: String, profile: CloudProfile) {
            if (mobile == null) mobile = profile.mobileNumber
            restoredVehicleSyncId = profile.activeVehicleSyncId
        }
    }

    /** Server time is a counter, so "changed since" is exact. */
    private class FakeCloud : CloudStore {
        private val records = mutableMapOf<String, MutableMap<String, MutableMap<String, CloudDocument>>>()
        private val profiles = mutableMapOf<String, MutableMap<String, Any?>>()
        private var serverTime = 1_000L
        var offline = false
        var duringWrite: (suspend (String) -> Unit)? = null

        fun count(uid: String) = records[uid].orEmpty().values.sumOf { it.size }

        fun document(uid: String, kind: String, id: String) = records[uid]?.get(kind)?.get(id)?.data

        override suspend fun changedSince(uid: String, kind: String, sinceMillis: Long?): List<CloudDocument> {
            check()
            return records[uid]?.get(kind).orEmpty().values
                .filter { sinceMillis == null || it.serverTimeMillis!! >= sinceMillis }
                .sortedBy { it.serverTimeMillis }
        }

        override suspend fun write(uid: String, kind: String, documents: Map<String, Map<String, Any?>>) {
            check()
            duringWrite?.invoke(kind)
            val collection = records.getOrPut(uid) { mutableMapOf() }.getOrPut(kind) { mutableMapOf() }
            for ((id, data) in documents) collection[id] = CloudDocument(id, data, serverTime++)
        }

        override suspend fun readProfile(uid: String): Map<String, Any?>? {
            check()
            return profiles[uid]
        }

        override suspend fun writeProfile(uid: String, data: Map<String, Any?>) {
            check()
            profiles.getOrPut(uid) { mutableMapOf() }.putAll(data)
        }

        override suspend fun deleteEverything(uid: String) {
            check()
            records.remove(uid)
            profiles.remove(uid)
        }

        private fun check() {
            if (offline) throw CloudException(SyncProblem.OFFLINE)
        }
    }

    private companion object {
        const val UID = "rider-1"
        val DAY: LocalDate = LocalDate.of(2026, 10, 21)

        fun vehicle(name: String, updatedAt: Long) = Vehicle(
            id = 0,
            name = name,
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
            updatedAtMillis = updatedAt,
        )

        fun trip(vehicleId: Long, fare: Long, updatedAt: Long) = Trip(
            id = 0,
            vehicleId = vehicleId,
            createdAtMillis = 1,
            updatedAtMillis = updatedAt,
            tripDateTime = Instant.parse("2026-10-21T11:12:00Z"),
            platform = Platform.UBER,
            status = TripStatus.COMPLETED,
            fareCents = fare,
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

        fun fuel(vehicleId: Long) = FuelLog(0, vehicleId, DAY, 4.5, 30_000, 135_000, 12_345.6, true, 1, 1)

        fun expense(vehicleId: Long) = Expense(0, vehicleId, DAY, ExpenseCategory.PARKING, 10_000, null, null, 1, 1)

        fun income() = OtherIncome(0, DAY, IncomeType.BONUS, Platform.PICKME, 150_000, "Sample bonus", 1, 1)
    }
}
