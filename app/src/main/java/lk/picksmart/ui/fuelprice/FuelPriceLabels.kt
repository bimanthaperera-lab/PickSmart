package lk.picksmart.ui.fuelprice

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.picksmart.R
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.Vehicle
import lk.picksmart.ui.components.rememberRecordDate
import lk.picksmart.util.todayInColombo

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
    val published = stringResource(R.string.fuel_price_source_published, rememberRecordDate(date, todayInColombo()))
    return if (listStale) stringResource(R.string.fuel_price_source_stale, published) else published
}
