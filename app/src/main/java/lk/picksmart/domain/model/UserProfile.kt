package lk.picksmart.domain.model

import java.time.LocalDate

/** How the rider signs in (CLAUDE.md section 14). */
enum class SignInMethod { EMAIL, GOOGLE }

/** The gender a rider chose on their profile. Optional; a rider can also say they'd rather not. */
enum class Gender { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }

/**
 * What the rider added to their profile themself, kept on the device only (`docs/decisions.md`,
 * 2026-10-26). Every part is optional.
 */
data class PersonalDetails(
    val mobile: String? = null,
    val gender: Gender? = null,
    val birthDate: LocalDate? = null,
)

/**
 * The signed-in rider, as the sign-in provider knows them. The optional mobile number isn't part
 * of it: the rider types that, and it is kept on the device.
 */
data class UserProfile(
    val uid: String,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
    val method: SignInMethod,
)
