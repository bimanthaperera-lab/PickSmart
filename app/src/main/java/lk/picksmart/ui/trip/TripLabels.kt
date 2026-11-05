package lk.picksmart.ui.trip

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import lk.picksmart.R
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripStatus

@get:StringRes
val Platform.label: Int
    get() = when (this) {
        Platform.UBER -> R.string.platform_uber
        Platform.PICKME -> R.string.platform_pickme
        Platform.COURIER -> R.string.platform_courier
        Platform.OWN_DELIVERY -> R.string.platform_own_delivery
        Platform.OTHER -> R.string.platform_other
    }

/** Two letters for the round badge in lists, e.g. "PM". */
@get:StringRes
val Platform.initials: Int
    get() = when (this) {
        Platform.UBER -> R.string.platform_uber_initials
        Platform.PICKME -> R.string.platform_pickme_initials
        Platform.COURIER -> R.string.platform_courier_initials
        Platform.OWN_DELIVERY -> R.string.platform_own_delivery_initials
        Platform.OTHER -> R.string.platform_other_initials
    }

@get:StringRes
val TripStatus.label: Int
    get() = when (this) {
        TripStatus.OFFER_CHECK -> R.string.trip_status_offer
        TripStatus.COMPLETED -> R.string.trip_status_completed
        TripStatus.CANCELLED -> R.string.trip_status_cancelled
    }

@get:StringRes
val IncomeType.label: Int
    get() = when (this) {
        IncomeType.INCENTIVE -> R.string.income_type_incentive
        IncomeType.TIP -> R.string.income_type_tip
        IncomeType.BONUS -> R.string.income_type_bonus
        IncomeType.OTHER -> R.string.income_type_other
    }

/** A short line under the type on Add income saying what it covers. */
@get:StringRes
val IncomeType.hint: Int
    get() = when (this) {
        IncomeType.INCENTIVE -> R.string.income_type_incentive_hint
        IncomeType.TIP -> R.string.income_type_tip_hint
        IncomeType.BONUS -> R.string.income_type_bonus_hint
        IncomeType.OTHER -> R.string.income_type_other_hint
    }

@get:DrawableRes
val IncomeType.icon: Int
    get() = when (this) {
        IncomeType.INCENTIVE -> R.drawable.ic_gift
        IncomeType.TIP -> R.drawable.ic_thumb_up
        IncomeType.BONUS -> R.drawable.ic_star
        IncomeType.OTHER -> R.drawable.ic_wallet
    }
