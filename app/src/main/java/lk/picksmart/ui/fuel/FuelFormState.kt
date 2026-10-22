package lk.picksmart.ui.fuel

import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.FuelLogInput
import lk.picksmart.ui.components.FieldError
import lk.picksmart.ui.components.positiveCentsError
import lk.picksmart.ui.components.positiveDecimalError
import lk.picksmart.util.centsToInput
import lk.picksmart.util.decimalToInput
import lk.picksmart.util.hundredthsToInput
import lk.picksmart.util.parseDecimal
import lk.picksmart.util.parseRupeesToCents
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Which of the two amounts the rider typed. The other is worked out from it and the price. */
enum class FuelAmount { TOTAL, LITRES }

enum class FuelField { TOTAL, LITRES, PRICE, ODOMETER }

/**
 * What the rider has typed on Add / Edit fill-up. Values stay text until [validate].
 *
 * A rider usually knows what they paid ("Rs. 2,000 of petrol") or the litres on the pump, plus the
 * price per litre. They type one amount and the other fills in: change it with [withTotal],
 * [withLitres] and [withPrice], which keep the pair in step.
 */
data class FuelFormState(
    val date: LocalDate,
    val price: String = "",
    val total: String = "",
    val litres: String = "",
    val typed: FuelAmount = FuelAmount.TOTAL,
    val odometer: String = "",
    val fullTank: Boolean = false,
)

fun FuelFormState.withTotal(text: String) = copy(total = text, typed = FuelAmount.TOTAL).recalculated()

fun FuelFormState.withLitres(text: String) = copy(litres = text, typed = FuelAmount.LITRES).recalculated()

fun FuelFormState.withPrice(text: String) = copy(price = text).recalculated()

/** Fills in the amount the rider didn't type, or clears it when it can't be worked out. */
private fun FuelFormState.recalculated(): FuelFormState {
    val priceCents = parseRupeesToCents(price)?.takeIf { it > 0 }
    return when (typed) {
        FuelAmount.TOTAL -> copy(litres = litresFor(parseRupeesToCents(total), priceCents)?.let(::decimalToInput).orEmpty())
        FuelAmount.LITRES -> copy(total = totalFor(parseDecimal(litres), priceCents)?.let(::hundredthsToInput).orEmpty())
    }
}

/** Litres bought for [totalCents] at [priceCents] a litre, to two decimals as on a pump. */
internal fun litresFor(totalCents: Long?, priceCents: Long?): Double? {
    if (totalCents == null || priceCents == null) return null
    return BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(priceCents), 2, RoundingMode.HALF_UP).toDouble()
}

/** The cost of [litres] at [priceCents] a litre, rounded half up to the cent. Null if too large. */
internal fun totalFor(litres: Double?, priceCents: Long?): Long? {
    if (litres == null || priceCents == null) return null
    val cents = BigDecimal.valueOf(litres).multiply(BigDecimal.valueOf(priceCents)).setScale(0, RoundingMode.HALF_UP)
    return if (cents <= BigDecimal.valueOf(Long.MAX_VALUE)) cents.longValueExact() else null
}

sealed interface FuelFormResult {
    data class Valid(val input: FuelLogInput) : FuelFormResult
    data class Invalid(val errors: Map<FuelField, FieldError>) : FuelFormResult
}

/**
 * Amount paid, litres and price must all be above 0; the odometer is optional. While the price is
 * missing, only the price and the amount the rider typed are flagged: the other amount is empty
 * just because it can't be worked out yet.
 */
fun FuelFormState.validate(): FuelFormResult {
    val errors = buildMap {
        positiveCentsError(price)?.let { put(FuelField.PRICE, it) }
        positiveCentsError(total)?.let { put(FuelField.TOTAL, it) }
        positiveDecimalError(litres)?.let { put(FuelField.LITRES, it) }
        if (odometer.isNotBlank() && parseDecimal(odometer) == null) put(FuelField.ODOMETER, FieldError.INVALID)
        if (FuelField.PRICE in this) remove(if (typed == FuelAmount.TOTAL) FuelField.LITRES else FuelField.TOTAL)
    }
    if (errors.isNotEmpty()) return FuelFormResult.Invalid(errors)
    return FuelFormResult.Valid(
        FuelLogInput(
            date = date,
            litres = parseDecimal(litres)!!,
            pricePerLitreCents = parseRupeesToCents(price)!!,
            totalCents = parseRupeesToCents(total)!!,
            odometerKm = parseDecimal(odometer),
            fullTank = fullTank,
        ),
    )
}

/** A new fill-up starts from today and the vehicle's current price. */
fun newFuelForm(today: LocalDate, vehiclePriceCents: Long) = FuelFormState(date = today, price = centsToInput(vehiclePriceCents))

/** A saved fill-up shows exactly what was stored; nothing is recalculated until the rider types. */
fun FuelLog.toFormState() = FuelFormState(
    date = date,
    price = centsToInput(pricePerLitreCents),
    total = hundredthsToInput(totalCents),
    litres = decimalToInput(litres),
    typed = FuelAmount.TOTAL,
    odometer = odometerKm?.let(::decimalToInput).orEmpty(),
    fullTank = fullTank,
)

/** Fuel spending dated in [month]. */
fun List<FuelLog>.totalInMonth(month: YearMonth): Long =
    filter { YearMonth.from(it.date) == month }.sumOf { it.totalCents }
