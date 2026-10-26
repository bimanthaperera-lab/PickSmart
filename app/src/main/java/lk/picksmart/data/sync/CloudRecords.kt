package lk.picksmart.data.sync

import lk.picksmart.data.local.entity.ExpenseEntity
import lk.picksmart.data.local.entity.FuelLogEntity
import lk.picksmart.data.local.entity.OtherIncomeEntity
import lk.picksmart.data.local.entity.TripEntity
import lk.picksmart.data.local.entity.VehicleEntity
import lk.picksmart.domain.model.CommissionType
import lk.picksmart.domain.model.ExpenseCategory
import lk.picksmart.domain.model.FareMode
import lk.picksmart.domain.model.FuelPriceSource
import lk.picksmart.domain.model.FuelType
import lk.picksmart.domain.model.IncomeType
import lk.picksmart.domain.model.Platform
import lk.picksmart.domain.model.TripStatus
import lk.picksmart.domain.model.VehicleType
import java.time.LocalDate

/**
 * How records look in the cloud: one document per record under the rider's own id, named by its
 * sync id, holding only what the rider entered (CLAUDE.md section 14). Money stays in cents,
 * dates are ISO text, enums are stored by name, and a record points at its vehicle by the
 * vehicle's sync id, because Room ids differ per phone.
 *
 * Reading is defensive: a document with a missing or unknown value (for example an enum added by
 * a newer app version) gives null and is skipped, never a crash or a half-filled record.
 */
object CloudRecords {
    const val UPDATED_AT = "updatedAt"
    const val DELETED = "deleted"
    const val FORMAT = "format"

    /** The document layout below. Raise it, and keep reading older ones, when the layout changes. */
    const val FORMAT_VERSION = 1L

    /** A deleted record keeps only this, so its contents leave the cloud with it. */
    fun deleted(deletedAtMillis: Long): Map<String, Any?> =
        mapOf(FORMAT to FORMAT_VERSION, UPDATED_AT to deletedAtMillis, DELETED to true)

    fun isDeleted(data: Map<String, Any?>): Boolean = data[DELETED] == true

    /** The record's `updatedAt`, or null when the document is malformed. */
    fun updatedAt(data: Map<String, Any?>): Long? = data.long(UPDATED_AT)

    fun vehicle(row: VehicleEntity): Map<String, Any?> = record(row.createdAtMillis, row.updatedAtMillis) + mapOf(
        "name" to row.name,
        "type" to row.type.name,
        "fuelType" to row.fuelType.name,
        "fuelPricePerLitreCents" to row.fuelPricePerLitreCents,
        "fuelPriceSource" to row.fuelPriceSource.name,
        "fuelPriceEffectiveDate" to row.fuelPriceEffectiveDate?.toString(),
        "autoUpdateFuelPrice" to row.autoUpdateFuelPrice,
        "fuelEfficiencyKmPerLitre" to row.fuelEfficiencyKmPerLitre,
        "maintenanceReservePerKmCents" to row.maintenanceReservePerKmCents,
        "targetProfitPerKmCents" to row.targetProfitPerKmCents,
        "isSample" to row.isSample,
        "archived" to row.archived,
        "plateNumber" to row.plateNumber,
    )

    fun vehicle(syncId: String, data: Map<String, Any?>, localId: Long): VehicleEntity? = read {
        VehicleEntity(
            id = localId,
            name = data.text("name"),
            type = data.enum<VehicleType>("type"),
            fuelType = data.enum<FuelType>("fuelType"),
            fuelPricePerLitreCents = data.cents("fuelPricePerLitreCents"),
            fuelPriceSource = data.enum<FuelPriceSource>("fuelPriceSource"),
            fuelPriceEffectiveDate = data.optionalDate("fuelPriceEffectiveDate"),
            autoUpdateFuelPrice = data.flag("autoUpdateFuelPrice"),
            fuelEfficiencyKmPerLitre = data.positive("fuelEfficiencyKmPerLitre"),
            maintenanceReservePerKmCents = data.cents("maintenanceReservePerKmCents"),
            targetProfitPerKmCents = data.optionalCents("targetProfitPerKmCents"),
            isSample = data.flag("isSample"),
            archived = data.flag("archived"),
            createdAtMillis = data.required("createdAt"),
            updatedAtMillis = data.required(UPDATED_AT),
            plateNumber = data.optionalText("plateNumber"),
            syncId = syncId,
            syncPending = false,
        )
    }

    fun trip(row: TripEntity, vehicleSyncId: String): Map<String, Any?> = record(row.createdAtMillis, row.updatedAtMillis) + mapOf(
        "vehicleSyncId" to vehicleSyncId,
        "tripDateTime" to row.tripDateTimeMillis,
        "platform" to row.platform.name,
        "status" to row.status.name,
        "fareCents" to row.fareCents,
        "fareMode" to row.fareMode.name,
        "commissionType" to row.commissionType.name,
        "commissionHundredths" to row.commissionHundredths,
        "pickupKm" to row.pickupKm,
        "tripKm" to row.tripKm,
        "returnKm" to row.returnKm,
        "otherCostsCents" to row.otherCostsCents,
        "cancellationFeeCents" to row.cancellationFeeCents,
        "notes" to row.notes,
        "fuelPricePerLitreCents" to row.fuelPricePerLitreCentsSnapshot,
        "fuelEfficiencyKmPerLitre" to row.fuelEfficiencySnapshot,
        "maintenanceReservePerKmCents" to row.maintenanceReservePerKmCentsSnapshot,
        "targetProfitPerKmCents" to row.targetProfitPerKmCentsSnapshot,
    )

    /** [vehicleIdFor] turns a vehicle's sync id into its id on this phone, or null if it isn't here. */
    fun trip(syncId: String, data: Map<String, Any?>, localId: Long, vehicleIdFor: (String) -> Long?): TripEntity? = read {
        TripEntity(
            id = localId,
            vehicleId = vehicleIdFor(data.text("vehicleSyncId")) ?: throw Malformed(),
            createdAtMillis = data.required("createdAt"),
            updatedAtMillis = data.required(UPDATED_AT),
            tripDateTimeMillis = data.required("tripDateTime"),
            platform = data.enum<Platform>("platform"),
            status = data.enum<TripStatus>("status"),
            fareCents = data.cents("fareCents"),
            fareMode = data.enum<FareMode>("fareMode"),
            commissionType = data.enum<CommissionType>("commissionType"),
            commissionHundredths = data.cents("commissionHundredths"),
            pickupKm = data.distance("pickupKm"),
            tripKm = data.distance("tripKm"),
            returnKm = data.distance("returnKm"),
            otherCostsCents = data.cents("otherCostsCents"),
            cancellationFeeCents = data.optionalCents("cancellationFeeCents"),
            notes = data.optionalText("notes"),
            fuelPricePerLitreCentsSnapshot = data.cents("fuelPricePerLitreCents"),
            fuelEfficiencySnapshot = data.positive("fuelEfficiencyKmPerLitre"),
            maintenanceReservePerKmCentsSnapshot = data.cents("maintenanceReservePerKmCents"),
            targetProfitPerKmCentsSnapshot = data.optionalCents("targetProfitPerKmCents"),
            syncId = syncId,
            syncPending = false,
        )
    }

    fun otherIncome(row: OtherIncomeEntity): Map<String, Any?> = record(row.createdAtMillis, row.updatedAtMillis) + mapOf(
        "date" to row.date.toString(),
        "type" to row.type.name,
        "platform" to row.platform?.name,
        "amountCents" to row.amountCents,
        "note" to row.note,
    )

    fun otherIncome(syncId: String, data: Map<String, Any?>, localId: Long): OtherIncomeEntity? = read {
        OtherIncomeEntity(
            id = localId,
            date = data.date("date"),
            type = data.enum<IncomeType>("type"),
            platform = data.optionalEnum<Platform>("platform"),
            amountCents = data.cents("amountCents"),
            note = data.optionalText("note"),
            createdAtMillis = data.required("createdAt"),
            updatedAtMillis = data.required(UPDATED_AT),
            syncId = syncId,
            syncPending = false,
        )
    }

    fun fuelLog(row: FuelLogEntity, vehicleSyncId: String): Map<String, Any?> = record(row.createdAtMillis, row.updatedAtMillis) + mapOf(
        "vehicleSyncId" to vehicleSyncId,
        "date" to row.date.toString(),
        "litres" to row.litres,
        "pricePerLitreCents" to row.pricePerLitreCents,
        "totalCents" to row.totalCents,
        "odometerKm" to row.odometerKm,
        "fullTank" to row.fullTank,
    )

    fun fuelLog(syncId: String, data: Map<String, Any?>, localId: Long, vehicleIdFor: (String) -> Long?): FuelLogEntity? = read {
        FuelLogEntity(
            id = localId,
            vehicleId = vehicleIdFor(data.text("vehicleSyncId")) ?: throw Malformed(),
            date = data.date("date"),
            litres = data.distance("litres"),
            pricePerLitreCents = data.cents("pricePerLitreCents"),
            totalCents = data.cents("totalCents"),
            odometerKm = data.optionalDistance("odometerKm"),
            fullTank = data.flag("fullTank"),
            createdAtMillis = data.required("createdAt"),
            updatedAtMillis = data.required(UPDATED_AT),
            syncId = syncId,
            syncPending = false,
        )
    }

    /** An expense's vehicle is optional; [vehicleSyncId] is null when it has none. */
    fun expense(row: ExpenseEntity, vehicleSyncId: String?): Map<String, Any?> = record(row.createdAtMillis, row.updatedAtMillis) + mapOf(
        "vehicleSyncId" to vehicleSyncId,
        "date" to row.date.toString(),
        "category" to row.category.name,
        "amountCents" to row.amountCents,
        "note" to row.note,
        "odometerKm" to row.odometerKm,
    )

    fun expense(syncId: String, data: Map<String, Any?>, localId: Long, vehicleIdFor: (String) -> Long?): ExpenseEntity? = read {
        ExpenseEntity(
            id = localId,
            vehicleId = data.optionalText("vehicleSyncId")?.let { vehicleIdFor(it) ?: throw Malformed() },
            date = data.date("date"),
            category = data.enum<ExpenseCategory>("category"),
            amountCents = data.cents("amountCents"),
            note = data.optionalText("note"),
            odometerKm = data.optionalDistance("odometerKm"),
            createdAtMillis = data.required("createdAt"),
            updatedAtMillis = data.required(UPDATED_AT),
            syncId = syncId,
            syncPending = false,
        )
    }

    private fun record(createdAt: Long, updatedAt: Long): Map<String, Any?> =
        mapOf(FORMAT to FORMAT_VERSION, "createdAt" to createdAt, UPDATED_AT to updatedAt, DELETED to false)

    /** Thrown inside [read] for a missing or invalid value. */
    private class Malformed : RuntimeException()

    private inline fun <T> read(block: () -> T): T? = try {
        block()
    } catch (e: Malformed) {
        null
    } catch (e: IllegalArgumentException) {
        // An unknown enum name, or a date that isn't ISO text.
        null
    } catch (e: java.time.DateTimeException) {
        null
    }

    private fun Map<String, Any?>.required(key: String): Long = long(key) ?: throw Malformed()

    private fun Map<String, Any?>.cents(key: String): Long = required(key).takeIf { it >= 0 } ?: throw Malformed()

    private fun Map<String, Any?>.optionalCents(key: String): Long? = if (this[key] == null) null else cents(key)

    private fun Map<String, Any?>.distance(key: String): Double =
        (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 } ?: throw Malformed()

    private fun Map<String, Any?>.optionalDistance(key: String): Double? = if (this[key] == null) null else distance(key)

    private fun Map<String, Any?>.positive(key: String): Double = distance(key).takeIf { it > 0 } ?: throw Malformed()

    private fun Map<String, Any?>.flag(key: String): Boolean = this[key] as? Boolean ?: throw Malformed()

    private fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: throw Malformed()

    private fun Map<String, Any?>.optionalText(key: String): String? = if (this[key] == null) null else text(key)

    private fun Map<String, Any?>.date(key: String): LocalDate = LocalDate.parse(text(key))

    private fun Map<String, Any?>.optionalDate(key: String): LocalDate? = optionalText(key)?.let(LocalDate::parse)

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String): E = enumValueOf(text(key))

    private inline fun <reified E : Enum<E>> Map<String, Any?>.optionalEnum(key: String): E? = optionalText(key)?.let { enumValueOf<E>(it) }
}

/** Firestore gives whole numbers back as Long, and may give a Double for a number it was sent. */
private fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
    else -> null
}
