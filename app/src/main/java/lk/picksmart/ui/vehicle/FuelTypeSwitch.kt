package lk.picksmart.ui.vehicle

import lk.picksmart.domain.model.FuelType
import lk.picksmart.util.centsToInput
import lk.picksmart.util.parseRupeesToCents

/**
 * The fuel price text after the rider switches from fuel type [from] to [to], given the prices
 * from `fuelPriceSuggestions`:
 *
 * - [to] has a suggested price: that price.
 * - Otherwise, when [typed] is just the suggestion for [from], it is cleared, so Petrol 92's price
 *   never stands in for Petrol 95.
 * - Otherwise it is something the rider typed for the new type, and it stays.
 */
fun priceAfterFuelTypeSwitch(typed: String, from: FuelType, to: FuelType, suggestions: Map<FuelType, Long>): String {
    suggestions[to]?.let { return centsToInput(it) }
    val fromSuggestion = suggestions[from]
    return if (fromSuggestion != null && parseRupeesToCents(typed) == fromSuggestion) "" else typed
}
