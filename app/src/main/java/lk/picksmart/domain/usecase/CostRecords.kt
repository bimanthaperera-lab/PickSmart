package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.Expense
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.ExpenseInput
import lk.picksmart.domain.model.FuelLog
import lk.picksmart.domain.model.FuelLogInput

fun newFuelLog(input: FuelLogInput, vehicleId: Long, nowMillis: Long) = FuelLog(
    id = 0,
    vehicleId = vehicleId,
    date = input.date,
    litres = input.litres,
    pricePerLitreCents = input.pricePerLitreCents,
    totalCents = input.totalCents,
    odometerKm = input.odometerKm,
    fullTank = input.fullTank,
    createdAtMillis = nowMillis,
    updatedAtMillis = nowMillis,
)

/** The id, vehicle and creation time never change. */
fun applyFuelLogEdit(current: FuelLog, input: FuelLogInput, nowMillis: Long): FuelLog =
    newFuelLog(input, current.vehicleId, nowMillis).copy(id = current.id, createdAtMillis = current.createdAtMillis)

/**
 * A new expense. Blank notes are stored as null. The odometer is only kept for maintenance and
 * repairs, so a reading typed before switching category doesn't stay hidden in the record.
 */
fun newExpense(input: ExpenseInput, vehicleId: Long?, nowMillis: Long) = Expense(
    id = 0,
    vehicleId = vehicleId,
    date = input.date,
    category = input.category,
    amountCents = input.amountCents,
    note = input.note?.trim()?.ifEmpty { null },
    odometerKm = input.odometerKm.takeIf { input.category == ExpenseCategory.MAINTENANCE_REPAIR },
    createdAtMillis = nowMillis,
    updatedAtMillis = nowMillis,
)

/** The id, vehicle and creation time never change. */
fun applyExpenseEdit(current: Expense, input: ExpenseInput, nowMillis: Long): Expense =
    newExpense(input, current.vehicleId, nowMillis).copy(id = current.id, createdAtMillis = current.createdAtMillis)
