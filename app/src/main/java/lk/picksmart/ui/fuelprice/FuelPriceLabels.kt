package lk.picksmart.ui.fuelprice

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.domain.model.FuelPriceList
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.util.todayInColombo
import java.time.LocalDate

/**
 * Where the vehicle's fuel price came from (CLAUDE.md section 8a): "Set by you", or "CPC price
 * from 1 Oct", with "· may be out of date" when the published list is over 45 days old.
 */
@Composable
fun fuelPriceSourceText(vehicle: Vehicle, listStale: Boolean): String {
    val date = vehicle.fuelPriceEffectiveDate
    if (vehicle.fuelPriceSource == FuelPriceSource.MANUAL || date == null) {
        return stringResource(R.string.fuel_price_source_manual)
    }
    return publishedSourceText(date, listStale)
}

/** "CPC price from 1 Oct", for a price taken from [list]. */
@Composable
fun publishedSourceText(list: FuelPriceList?, listStale: Boolean): String =
    list?.let { publishedSourceText(it.effectiveFrom, listStale) } ?: stringResource(R.string.fuel_price_source_manual)

@Composable
private fun publishedSourceText(effectiveFrom: LocalDate, listStale: Boolean): String {
    val published = stringResource(R.string.fuel_price_source_published, rememberRecordDate(effectiveFrom, todayInColombo()))
    return if (listStale) stringResource(R.string.fuel_price_source_stale, published) else published
}
