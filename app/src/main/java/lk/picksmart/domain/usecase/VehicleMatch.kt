package lk.picksmart.domain.usecase

import java.util.Locale
import lk.picksmart.domain.model.VehicleType

/** What tells a rider's vehicles apart, for finding a restored vehicle among those on the phone. */
data class VehicleIdentity(val name: String, val type: VehicleType, val plateNumber: String?)

/**
 * The vehicle in [candidates] that is the same as [restored], or null (owner decision 2026-10-27),
 * so a backup restored over vehicles the rider already made on this phone updates them rather
 * than adding a second copy:
 *
 * - one with the same number plate, when both have one (spaces, dashes and case don't matter);
 * - otherwise one of the same type with the same name (case and extra spaces don't matter), as
 *   long as the two don't have different plates.
 *
 * A plate match wins over a name match.
 */
fun <T> matchVehicle(restored: VehicleIdentity, candidates: List<T>, identity: (T) -> VehicleIdentity): T? {
    val plate = restored.plateNumber?.let(::plateKey)
    if (plate != null) {
        candidates.firstOrNull { identity(it).plateNumber?.let(::plateKey) == plate }?.let { return it }
    }
    val name = nameKey(restored.name)
    return candidates.firstOrNull {
        val candidate = identity(it)
        candidate.type == restored.type &&
            nameKey(candidate.name) == name &&
            (plate == null || candidate.plateNumber?.let(::plateKey) == null)
    }
}

private fun plateKey(plate: String): String? = plate.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT).ifEmpty { null }

private fun nameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
