package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.Vehicle
import java.time.LocalDate

/** How long an alert stays in the inbox (CLAUDE.md section 9a). */
const val ALERT_KEEP_DAYS = 30L

/** A fuel price change the alerts inbox tells the rider about, once ([key]). */
sealed interface FuelPriceNews {
    val vehicle: Vehicle
    val key: String

    /** A published price differs from the vehicle's and waits for "Update" or "Keep". */
    data class NewPrice(override val vehicle: Vehicle, val priceCents: Long, val effectiveFrom: LocalDate) : FuelPriceNews {
        override val key get() = "fuel_price_new:${vehicle.syncId}:$effectiveFrom"
    }

    /** A published price was applied because the rider turned auto-update on. */
    data class Applied(override val vehicle: Vehicle, val effectiveFrom: LocalDate) : FuelPriceNews {
        override val key get() = "fuel_price_applied:${vehicle.syncId}:$effectiveFrom"
    }
}

/**
 * The fuel price news for the active vehicle in [state], the same news Home's notice shows, or
 * null when there is none. Each price list is told once per vehicle, by [FuelPriceNews.key].
 */
fun fuelPriceNews(state: FuelPriceState): FuelPriceNews? {
    val vehicle = state.vehicle ?: return null
    val offer = state.action as? PublishedPriceAction.Offer
    val appliedFrom = vehicle.fuelPriceEffectiveDate
    return when {
        offer != null -> FuelPriceNews.NewPrice(vehicle, offer.priceCents, offer.effectiveFrom)
        state.autoApplied && appliedFrom != null -> FuelPriceNews.Applied(vehicle, appliedFrom)
        else -> null
    }
}

/** Alerts created before this time are deleted. */
fun alertCutoffMillis(nowMillis: Long): Long = nowMillis - ALERT_KEEP_DAYS * 24 * 60 * 60 * 1000
