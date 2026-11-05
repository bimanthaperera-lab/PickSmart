package lk.picksmart.ui.notifications

import android.content.Context
import lk.picksmart.R
import lk.picksmart.data.preferences.AppLanguageStore
import lk.picksmart.domain.model.Alert
import lk.picksmart.domain.model.AlertLink
import lk.picksmart.domain.model.AlertType
import lk.picksmart.domain.usecase.FuelPriceNews
import lk.picksmart.ui.vehicle.label
import lk.picksmart.util.MoneyFormatter
import lk.picksmart.util.dateFormatter

/**
 * The inbox alert for [news], in the app's language, with the same words as Home's notice.
 * Tapping it opens the Fuel price screen.
 */
fun fuelPriceAlert(context: Context, news: FuelPriceNews, nowMillis: Long): Alert {
    val words = AppLanguageStore.localized(context)
    val money = MoneyFormatter(words.getString(R.string.currency_prefix))
    val fuel = words.getString(news.vehicle.fuelType.label)
    val (title, body) = when (news) {
        is FuelPriceNews.NewPrice -> words.getString(R.string.home_fuel_price_new_title) to words.getString(
            R.string.home_fuel_price_new_body,
            fuel,
            money.format(news.priceCents),
            money.format(news.vehicle.fuelPricePerLitreCents),
        )
        is FuelPriceNews.Applied -> {
            val locale = words.resources.configuration.locales[0]
            val date = news.effectiveFrom.format(dateFormatter(words.getString(R.string.pattern_short_date), locale))
            words.getString(R.string.fuel_price_auto_title) to words.getString(
                R.string.fuel_price_auto_body,
                fuel,
                money.format(news.vehicle.fuelPricePerLitreCents),
                date,
            )
        }
    }
    return Alert(
        type = AlertType.FUEL_PRICE,
        title = title,
        body = body,
        createdAtMillis = nowMillis,
        link = AlertLink.FUEL_PRICE,
        key = news.key,
    )
}
