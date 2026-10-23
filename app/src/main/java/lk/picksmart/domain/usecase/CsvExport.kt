package lk.picksmart.domain.usecase

import lk.picksmart.domain.calculator.ProfitCalculator
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.OtherIncome
import lk.picksmart.domain.model.Trip
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.util.ColomboZone
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * The CSV files a rider exports from Reports: one per record type, so each opens as a plain table
 * in Excel or Google Sheets. Headers and codes (`COMPLETED`, `PICKME`) are fixed English, so the
 * files stay the same whatever language the app is in. Amounts are plain rupees (`1250.00`), dates
 * ISO (`2026-10-21`), times 24-hour Colombo time. Rows end with CRLF (RFC 4180).
 *
 * Trips carry the calculator's lines too, worked out from each trip's own rate snapshot, and a
 * `counted` column that is `no` for offers, which never count as income (N1).
 */
object CsvExport {

    fun trips(trips: List<Trip>, vehicleNames: Map<Long, String>, calculator: ProfitCalculator): String = csv(
        listOf(
            "id", "date", "time", "vehicle", "platform", "status", "counted", "fare", "fare_mode", "commission_type",
            "commission_value", "pickup_km", "trip_km", "return_km", "other_costs", "cancellation_fee",
            "fuel_price_per_litre", "fuel_efficiency_km_per_litre", "maintenance_reserve_per_km",
            "est_commission", "est_fuel", "est_reserve", "est_net_profit", "notes",
        ),
        trips.sortedWith(compareBy({ it.tripDateTime }, { it.id })).map { trip ->
            val time = trip.tripDateTime.atZone(ColomboZone)
            val result = calculator.estimate(trip)
            listOf(
                trip.id.toString(),
                time.toLocalDate().toString(),
                time.format(TIME),
                text(vehicleNames[trip.vehicleId]),
                trip.platform.name,
                trip.status.name,
                if (trip.status == TripStatus.OFFER_CHECK) "no" else "yes",
                money(trip.fareCents),
                trip.fareMode.name,
                trip.commissionType.name,
                BigDecimal.valueOf(trip.commissionHundredths, 2).toPlainString() +
                    if (trip.commissionType == CommissionType.PERCENT) "%" else "",
                number(trip.pickupKm),
                number(trip.tripKm),
                number(trip.returnKm),
                money(trip.otherCostsCents),
                trip.cancellationFeeCents?.let(::money).orEmpty(),
                money(trip.rates.fuelPricePerLitreCents),
                number(trip.rates.fuelEfficiencyKmPerLitre),
                money(trip.rates.maintenanceReservePerKmCents),
                result?.let { money(it.commissionCents) }.orEmpty(),
                result?.let { money(it.fuelCostCents) }.orEmpty(),
                result?.let { money(it.maintenanceReserveCents) }.orEmpty(),
                result?.let { money(it.netProfitCents) }.orEmpty(),
                text(trip.notes),
            )
        },
    )

    fun otherIncome(income: List<OtherIncome>): String = csv(
        listOf("id", "date", "type", "platform", "amount", "note"),
        income.sortedWith(compareBy({ it.date }, { it.id })).map {
            listOf(it.id.toString(), it.date.toString(), it.type.name, it.platform?.name.orEmpty(), money(it.amountCents), text(it.note))
        },
    )

    fun fuelLog(logs: List<FuelLog>, vehicleNames: Map<Long, String>): String = csv(
        listOf("id", "date", "vehicle", "litres", "price_per_litre", "total", "odometer_km", "full_tank"),
        logs.sortedWith(compareBy({ it.date }, { it.id })).map {
            listOf(
                it.id.toString(),
                it.date.toString(),
                text(vehicleNames[it.vehicleId]),
                number(it.litres),
                money(it.pricePerLitreCents),
                money(it.totalCents),
                it.odometerKm?.let(::number).orEmpty(),
                if (it.fullTank) "yes" else "no",
            )
        },
    )

    fun expenses(expenses: List<Expense>, vehicleNames: Map<Long, String>): String = csv(
        listOf("id", "date", "vehicle", "category", "amount", "odometer_km", "note"),
        expenses.sortedWith(compareBy({ it.date }, { it.id })).map {
            listOf(
                it.id.toString(),
                it.date.toString(),
                text(it.vehicleId?.let(vehicleNames::get)),
                it.category.name,
                money(it.amountCents),
                it.odometerKm?.let(::number).orEmpty(),
                text(it.note),
            )
        },
    )

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    private fun money(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    private fun number(value: Double): String {
        val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        return if (plain == "-0") "0" else plain
    }

    /**
     * Text the rider typed. A leading `=`, `+`, `-` or `@` would make a spreadsheet run it as a
     * formula, so such text gets a leading apostrophe.
     */
    private fun text(value: String?): String = when {
        value == null -> ""
        value.firstOrNull() in FORMULA_STARTS -> "'$value"
        else -> value
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    private fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        (listOf(header) + rows).forEach { row ->
            append(row.joinToString(",", transform = ::field))
            append("\r\n")
        }
    }

    /** Quotes a field when it holds a comma, quote or line break, doubling any quotes. */
    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
