package lk.picksmart.domain.usecase

import lk.picksmart.domain.model.Vehicle

/** Active vehicles allowed without an account (CLAUDE.md section 9, "Vehicles"). */
const val SIGNED_OUT_VEHICLE_LIMIT = 1

/** Active vehicles allowed with a free account. Premium will lift this (Phase 8d). */
const val SIGNED_IN_VEHICLE_LIMIT = 3

/**
 * How many active (not archived) vehicles the rider may have. One small function of "signed in or
 * not" until `PlanProvider` exists (CLAUDE.md section 15), which will also return no limit for
 * Premium.
 */
fun vehicleLimit(signedIn: Boolean): Int = if (signedIn) SIGNED_IN_VEHICLE_LIMIT else SIGNED_OUT_VEHICLE_LIMIT

/**
 * Whether the rider may add another vehicle. Archived vehicles don't count. A rider already at or
 * over the limit (for example after signing out) keeps and uses every vehicle they have; they just
 * can't add one more. The limit never blocks checking or recording rides.
 */
fun canAddVehicle(vehicles: List<Vehicle>, limit: Int): Boolean = vehicles.count { !it.archived } < limit

/** What archiving a vehicle needs, from [archivePlan]. */
sealed interface ArchivePlan {
    /** It is the rider's only active vehicle, so it can't be archived. */
    data object LastVehicle : ArchivePlan

    /** It is the active vehicle: the rider picks which of [candidates] becomes active first. */
    data class ChooseActive(val candidates: List<Vehicle>) : ArchivePlan

    /** Another vehicle is active, so it can be archived straight away (after the confirmation). */
    data object Archive : ArchivePlan
}

/**
 * What archiving [vehicleId] needs, given the rider's [vehicles] and the active one,
 * [activeVehicleId]. The last active vehicle can't be archived; archiving the active one asks
 * which becomes active (CLAUDE.md section 9, "Vehicles"). Trips keep their vehicle and rate
 * snapshot either way, so History and Reports are unchanged.
 */
fun archivePlan(vehicles: List<Vehicle>, vehicleId: Long, activeVehicleId: Long?): ArchivePlan {
    val others = vehicles.filter { !it.archived && it.id != vehicleId }
    return when {
        others.isEmpty() -> ArchivePlan.LastVehicle
        vehicleId == activeVehicleId -> ArchivePlan.ChooseActive(others)
        else -> ArchivePlan.Archive
    }
}

/** Marks [current] archived. Its trips, fuel logs and expenses stay as they are. */
fun applyArchive(current: Vehicle, nowMillis: Long): Vehicle = current.copy(archived = true, updatedAtMillis = nowMillis)
