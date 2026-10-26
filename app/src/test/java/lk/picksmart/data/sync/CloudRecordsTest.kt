package lk.picksmart.data.sync

import lk.picksmart.data.local.entity.ExpenseEntity
import lk.picksmart.data.local.entity.FuelLogEntity
import lk.picksmart.data.local.entity.OtherIncomeEntity
import lk.picksmart.data.local.entity.TripEntity
import lk.picksmart.data.local.entity.VehicleEntity
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Sample data only. */
class CloudRecordsTest {

    private val vehicle = VehicleEntity(
        id = 7,
        name = "Bajaj RE",
        type = VehicleType.THREE_WHEELER,
        fuelType = FuelType.AUTO_DIESEL,
        fuelPricePerLitreCents = 39_200,
        fuelPriceSource = FuelPriceSource.PUBLISHED,
        fuelPriceEffectiveDate = LocalDate.of(2026, 10, 1),
        autoUpdateFuelPrice = true,
        fuelEfficiencyKmPerLitre = 30.0,
        maintenanceReservePerKmCents = 150,
        targetProfitPerKmCents = 5_000,
        isSample = false,
        archived = false,
        createdAtMillis = 1_000,
        updatedAtMillis = 2_000,
        plateNumber = "WP ABC-1234",
        syncId = "v1",
        syncPending = true,
    )

    private val trip = TripEntity(
        id = 3,
        vehicleId = 7,
        createdAtMillis = 1_000,
        updatedAtMillis = 3_000,
        tripDateTimeMillis = 1_761_000_000_000,
        platform = Platform.PICKME,
        status = TripStatus.CANCELLED,
        fareCents = 0,
        fareMode = FareMode.PAYOUT_AFTER_COMMISSION,
        commissionType = CommissionType.AMOUNT,
        commissionHundredths = 0,
        pickupKm = 2.5,
        tripKm = 0.0,
        returnKm = 0.5,
        otherCostsCents = 0,
        cancellationFeeCents = 10_000,
        notes = null,
        fuelPricePerLitreCentsSnapshot = 39_200,
        fuelEfficiencySnapshot = 30.0,
        maintenanceReservePerKmCentsSnapshot = 150,
        targetProfitPerKmCentsSnapshot = null,
        syncId = "t1",
        syncPending = true,
    )

    @Test
    fun `a vehicle reads back the same, keeping this phone's id`() {
        val data = CloudRecords.vehicle(vehicle)
        assertEquals(vehicle.copy(id = 42, syncPending = false), CloudRecords.vehicle("v1", data, localId = 42))
    }

    @Test
    fun `a trip points at its vehicle by sync id and reads back with this phone's vehicle id`() {
        val data = CloudRecords.trip(trip, vehicleSyncId = "v1")
        assertEquals("v1", data["vehicleSyncId"])
        assertFalse(data.containsKey("vehicleId"))
        val read = CloudRecords.trip("t1", data, localId = 0) { if (it == "v1") 99 else null }
        assertEquals(trip.copy(id = 0, vehicleId = 99, syncPending = false), read)
    }

    @Test
    fun `a trip whose vehicle isn't here is skipped`() {
        val data = CloudRecords.trip(trip, vehicleSyncId = "v1")
        assertNull(CloudRecords.trip("t1", data, localId = 0) { null })
    }

    @Test
    fun `income, fuel and expenses read back the same`() {
        val income = OtherIncomeEntity(4, LocalDate.of(2026, 10, 20), IncomeType.TIP, null, 15_050, "Sample", 1, 2, "i1", false)
        assertEquals(income, CloudRecords.otherIncome("i1", CloudRecords.otherIncome(income), 4))

        val fuel = FuelLogEntity(5, 7, LocalDate.of(2026, 10, 21), 4.5, 39_200, 176_400, null, true, 1, 2, "f1", false)
        assertEquals(fuel, CloudRecords.fuelLog("f1", CloudRecords.fuelLog(fuel, "v1"), 5) { 7 })

        val expense = ExpenseEntity(6, null, LocalDate.of(2026, 10, 22), ExpenseCategory.PARKING, 10_000, null, null, 1, 2, "e1", false)
        assertEquals(expense, CloudRecords.expense("e1", CloudRecords.expense(expense, null), 6) { error("no vehicle") })
    }

    @Test
    fun `whole numbers sent back as doubles still read`() {
        val data = CloudRecords.vehicle(vehicle).mapValues { (_, value) -> if (value is Long) value.toDouble() else value }
        assertEquals(39_200L, CloudRecords.vehicle("v1", data, 1)!!.fuelPricePerLitreCents)
    }

    @Test
    fun `missing, negative or unknown values give no record`() {
        val data = CloudRecords.vehicle(vehicle)
        assertNull(CloudRecords.vehicle("v1", data - "name", 1))
        assertNull(CloudRecords.vehicle("v1", data + ("fuelPricePerLitreCents" to -1L), 1))
        assertNull(CloudRecords.vehicle("v1", data + ("fuelEfficiencyKmPerLitre" to 0.0), 1))
        assertNull(CloudRecords.vehicle("v1", data + ("type" to "SPACESHIP"), 1))
        assertNull(CloudRecords.vehicle("v1", data + ("fuelPriceEffectiveDate" to "1 Oct"), 1))
        assertNull(CloudRecords.vehicle("v1", data + ("createdAt" to 1.5), 1))
    }

    @Test
    fun `a deleted record keeps only its time`() {
        val data = CloudRecords.deleted(5_000)
        assertTrue(CloudRecords.isDeleted(data))
        assertEquals(5_000L, CloudRecords.updatedAt(data))
        assertEquals(setOf(CloudRecords.FORMAT, CloudRecords.UPDATED_AT, CloudRecords.DELETED), data.keys)
        assertFalse(CloudRecords.isDeleted(CloudRecords.vehicle(vehicle)))
    }
}
