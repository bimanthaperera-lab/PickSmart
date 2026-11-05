package lk.picksmart.domain.model

/**
 * The language the app is shown in (CLAUDE.md section 5). [SYSTEM] follows the phone; the others
 * are fixed by the rider. [tag] is the BCP 47 language tag of the `values-` folder.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SINHALA("si"),
    TAMIL("ta"),
}
