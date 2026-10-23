package lk.picksmart.domain.model

import java.time.LocalDate

/**
 * The last valid published pump price list (CLAUDE.md section 8a). It has a price for every
 * [FuelType]; a file missing one is rejected before it gets here.
 *
 * @property effectiveFrom The date the prices took effect, from the file.
 * @property fetchedAtMillis When this list was last downloaded and accepted (UTC epoch millis).
 *   A download that returns the same list refreshes it, so an old value means the app has not
 *   reached the file for a while.
 */
data class FuelPriceList(
    val effectiveFrom: LocalDate,
    val fetchedAtMillis: Long,
    val pricesCentsPerLitre: Map<FuelType, Long>,
) {
    fun priceFor(type: FuelType): Long? = pricesCentsPerLitre[type]
}
