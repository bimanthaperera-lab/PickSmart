package lk.picksmart.domain.model

/** What an alert is about (CLAUDE.md section 9a). */
enum class AlertType { FUEL_PRICE, DAILY_SUMMARY, UNFINISHED_OFFERS, EFFICIENCY_SUGGESTION, OTHER }

/** The screen an alert opens when tapped. */
enum class AlertLink { FUEL_PRICE, REPORTS, HISTORY }

/**
 * One row in the in-app alerts inbox (CLAUDE.md section 9a). [title] and [body] are written in
 * the app's language at the time, like a notification.
 *
 * @property key Alerts with the same key are written only once, so a price change is told once
 *   however often the price is checked. Null for alerts that are never repeated.
 */
data class Alert(
    val id: Long = 0,
    val type: AlertType,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val read: Boolean = false,
    val link: AlertLink? = null,
    val key: String? = null,
)
