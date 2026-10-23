package lk.picksmart.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelType
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs

/** Why a downloaded price file was not accepted. The last valid list is kept in every case. */
enum class FuelPriceRejection {
    /** Not JSON, or not the expected shape. */
    MALFORMED,

    /** A `schema` this version doesn't know. */
    UNKNOWN_SCHEMA,

    /** A fuel type has no price, or the price isn't a whole number of cents. */
    MISSING_PRICE,

    /** A price is outside Rs. 50–2,000 per litre. */
    PRICE_OUT_OF_RANGE,

    /** A price moved by more than 40% from the last valid list. */
    CHANGE_TOO_LARGE,

    /** The file is dated before the list already saved, e.g. a stale cached copy. */
    OLDER_THAN_SAVED,
}

sealed interface FuelPriceParseResult {
    data class Valid(val list: FuelPriceList) : FuelPriceParseResult
    data class Rejected(val reason: FuelPriceRejection) : FuelPriceParseResult
}

/**
 * Reads and checks the published price file (CLAUDE.md section 8a):
 *
 * ```json
 * { "schema": 1, "effectiveFrom": "2026-10-01",
 *   "pricesCentsPerLitre": { "PETROL_92": 41400, "PETROL_95": 47500, "AUTO_DIESEL": 39200, "SUPER_DIESEL": 52800 } }
 * ```
 *
 * The file is rejected when the schema is unknown, a price is missing, a price is outside
 * Rs. 50–2,000 per litre, a price moved by more than 40% from [previous] (exactly 40% is
 * allowed), or it is dated before [previous]. Unknown fields and unknown fuel types are ignored,
 * so the owner can add to the file without breaking older installs.
 *
 * @param fetchedAtMillis Stored as [FuelPriceList.fetchedAtMillis] on success.
 */
fun parseFuelPriceFile(text: String, previous: FuelPriceList?, fetchedAtMillis: Long): FuelPriceParseResult {
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return FuelPriceParseResult.Rejected(FuelPriceRejection.MALFORMED)

    val schema = (root["schema"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        ?: return FuelPriceParseResult.Rejected(FuelPriceRejection.MALFORMED)
    if (schema != SUPPORTED_SCHEMA) return FuelPriceParseResult.Rejected(FuelPriceRejection.UNKNOWN_SCHEMA)

    val effectiveFrom = (root["effectiveFrom"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    } ?: return FuelPriceParseResult.Rejected(FuelPriceRejection.MALFORMED)

    val priceObject = root["pricesCentsPerLitre"] as? JsonObject
        ?: return FuelPriceParseResult.Rejected(FuelPriceRejection.MISSING_PRICE)
    val prices = FuelType.entries.associateWith { type ->
        (priceObject[type.name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            ?: return FuelPriceParseResult.Rejected(FuelPriceRejection.MISSING_PRICE)
    }

    if (prices.values.any { it !in MIN_PRICE_CENTS..MAX_PRICE_CENTS }) {
        return FuelPriceParseResult.Rejected(FuelPriceRejection.PRICE_OUT_OF_RANGE)
    }
    if (previous != null) {
        if (effectiveFrom < previous.effectiveFrom) return FuelPriceParseResult.Rejected(FuelPriceRejection.OLDER_THAN_SAVED)
        val tooLarge = prices.any { (type, cents) ->
            val old = previous.priceFor(type) ?: return@any false
            // |new − old| > 40% of old, in whole numbers. Prices are capped above, so no overflow.
            abs(cents - old) * 100 > old * MAX_CHANGE_PERCENT
        }
        if (tooLarge) return FuelPriceParseResult.Rejected(FuelPriceRejection.CHANGE_TOO_LARGE)
    }
    return FuelPriceParseResult.Valid(FuelPriceList(effectiveFrom, fetchedAtMillis, prices))
}

private const val SUPPORTED_SCHEMA = 1
private const val MIN_PRICE_CENTS = 5_000L
private const val MAX_PRICE_CENTS = 200_000L
private const val MAX_CHANGE_PERCENT = 40L
