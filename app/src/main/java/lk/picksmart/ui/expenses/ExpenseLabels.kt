package lk.picksmart.ui.expenses

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import lk.picksmart.R
import lk.picksmart.domain.model.ExpenseCategory

@get:StringRes
val ExpenseCategory.label: Int
    get() = when (this) {
        ExpenseCategory.MAINTENANCE_REPAIR -> R.string.expense_category_maintenance
        ExpenseCategory.PARKING -> R.string.expense_category_parking
        ExpenseCategory.MOBILE_DATA -> R.string.expense_category_mobile_data
        ExpenseCategory.TOLLS -> R.string.expense_category_tolls
        ExpenseCategory.OTHER -> R.string.expense_category_other
    }

@get:DrawableRes
val ExpenseCategory.icon: Int
    get() = when (this) {
        ExpenseCategory.MAINTENANCE_REPAIR -> R.drawable.ic_wrench
        ExpenseCategory.PARKING -> R.drawable.ic_parking
        ExpenseCategory.MOBILE_DATA -> R.drawable.ic_mobile
        ExpenseCategory.TOLLS -> R.drawable.ic_toll
        ExpenseCategory.OTHER -> R.drawable.ic_receipt
    }
