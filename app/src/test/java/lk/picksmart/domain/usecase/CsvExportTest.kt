package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripInput
import lk.picksmart.domain.model.TripRates
import lk.picksmart.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Sample data only. */
class CsvExportTest {

    private val calculator = ProfitCalculator()
    private val names = mapOf(1L to "Bike")

    /** G1 at 4:42 PM Colombo. */
    private val g1 = newTrip(
        TripInput(
            tripDateTime = Instant.parse("2026-10-21T11:12:00Z"),
            platform = Platform.PICKME,
            status = TripStatus.COMPLETED,
            fareCents = 100_000,
            fareMode = FareMode.GROSS_BEFORE_COMMISSION,
            commissionType = CommissionType.AMOUNT,
            commissionHundredths = 20_000,
            pickupKm = 2.0,
            tripKm = 8.0,
            returnKm = 0.0,
            otherCostsCents = 0,
            cancellationFeeCents = null,
            notes = null,
            rates = TripRates(30_000, 40.0, 200, null),
        ),
        vehicleId = 1,
        nowMillis = 1,
    ).copy(id = 7)

    private fun lines(csv: String) = csv.split("\r\n").dropLast(1)

    @Test
    fun tripsCarryTheCalculatorLinesAndMarkOffersAsNotCounted() {
        val offer = g1.copy(id = 8, status = TripStatus.OFFER_CHECK, commissionType = CommissionType.PERCENT, commissionHundredths = 2_050)
        val rows = lines(CsvExport.trips(listOf(offer, g1), names, calculator))
        assertEquals(3, rows.size)
        assertEquals(
            "id,date,time,vehicle,platform,status,counted,fare,fare_mode,commission_type,commission_value,pickup_km," +
                "trip_km,return_km,other_costs,cancellation_fee,fuel_price_per_litre,fuel_efficiency_km_per_litre," +
                "maintenance_reserve_per_km,est_commission,est_fuel,est_reserve,est_net_profit,notes",
            rows[0],
        )
        assertEquals(
            "7,2026-10-21,16:42,Bike,PICKME,COMPLETED,yes,1000.00,GROSS_BEFORE_COMMISSION,AMOUNT,200.00,2,8,0,0.00,," +
                "300.00,40,2.00,200.00,75.00,20.00,705.00,",
            rows[1],
        )
        assertEquals("no", rows[2].split(",")[6])
        assertEquals("20.50%", rows[2].split(",")[10])
    }

    @Test
    fun textIsQuotedAndFormulasAreDefused() {
        val trip = g1.copy(notes = "Airport, \"late\"\nnight")
        val row = lines(CsvExport.trips(listOf(trip), mapOf(1L to "=HYPERLINK(x)"), calculator))[1]
        assertTrue(row, row.endsWith(",\"Airport, \"\"late\"\"\nnight\""))
        assertEquals("'=HYPERLINK(x)", row.split(",")[3])
    }

    @Test
    fun otherRecordTypes() {
        val day = LocalDate.of(2026, 10, 21)
        assertEquals(
            listOf("id,date,type,platform,amount,note", "3,2026-10-21,TIP,,150.50,"),
            lines(CsvExport.otherIncome(listOf(OtherIncome(3, day, IncomeType.TIP, null, 15_050, null, 0, 0)))),
        )
        assertEquals(
            listOf(
                "id,date,vehicle,litres,price_per_litre,total,odometer_km,full_tank",
                "4,2026-10-21,Bike,3.5,300.00,1050.00,12345.6,yes",
            ),
            lines(CsvExport.fuelLog(listOf(FuelLog(4, 1, day, 3.5, 30_000, 105_000, 12_345.6, true, 0, 0)), names)),
        )
        assertEquals(
            listOf("id,date,vehicle,category,amount,odometer_km,note", "5,2026-10-21,,PARKING,100.00,,'-50 refund"),
            lines(CsvExport.expenses(listOf(Expense(5, null, day, ExpenseCategory.PARKING, 10_000, "-50 refund", null, 0, 0)), names)),
        )
    }

    @Test
    fun emptyListsGiveJustTheHeader() {
        assertEquals(1, lines(CsvExport.expenses(emptyList(), names)).size)
    }
}
