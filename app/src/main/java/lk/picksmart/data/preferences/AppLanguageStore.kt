package lk.picksmart.data.preferences

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import lk.picksmart.domain.model.AppLanguage
import java.util.Locale

/**
 * The rider's app language. On Android 13+ it is Android's own per-app language (which the phone's
 * Settings also show and change). Below that, Picksmart keeps it in a small SharedPreferences file
 * and applies it in [wrap]: the language must be known before the first screen is created, before
 * DataStore can be read. Auto Backup includes that file.
 */
object AppLanguageStore {

    private const val PREFS = "app_language"
    private const val KEY_TAG = "language_tag"

    fun current(context: Context): AppLanguage {
        val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            storedTag(context)
        }
        return fromTags(tags)
    }

    /**
     * Shows the app in [language] from now on. On 13+ Android sends the activity a configuration
     * change, which it handles in place (the screens redraw in the new language, with no restart
     * and no blank frame). Below 13 the language is applied in [wrap], so [activity] is recreated.
     */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, language.tag.orEmpty()).apply()
            activity.recreate()
        }
    }

    /** Below Android 13, [base] with the stored language applied; otherwise [base] unchanged. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base).takeIf { it.isNotBlank() } ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    /**
     * [base] in the rider's chosen language, on every Android version, or [base] for the phone's
     * language. For the offer reader: Android 13+ applies the per-app language to activities but not
     * to an accessibility service, whose badge and notification would otherwise stay in the phone's
     * language. Called on each use, so a language change shows on the next offer.
     */
    fun localized(base: Context): Context {
        val tag = current(base).tag ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    /**
     * The language the screens are showing now: the rider's choice, or for [AppLanguage.SYSTEM]
     * the phone's language when Picksmart has it, else English.
     */
    fun showing(context: Context): AppLanguage {
        val chosen = current(context)
        if (chosen != AppLanguage.SYSTEM) return chosen
        return fromTags(context.resources.configuration.locales[0].toLanguageTag()).takeIf { it != AppLanguage.SYSTEM }
            ?: AppLanguage.ENGLISH
    }

    private fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null).orEmpty()

    private fun fromTags(tags: String): AppLanguage {
        val first = tags.substringBefore(',').substringBefore('-').lowercase()
        return AppLanguage.entries.firstOrNull { it.tag == first } ?: AppLanguage.SYSTEM
    }
}
