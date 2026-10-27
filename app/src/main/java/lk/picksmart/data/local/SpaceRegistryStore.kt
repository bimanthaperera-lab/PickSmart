package lk.picksmart.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import lk.picksmart.domain.usecase.FIRST_SPACE
import lk.picksmart.domain.usecase.SpaceRegistry
import java.util.UUID

/**
 * Keeps the [SpaceRegistry] (`DataSpaces.kt`): which space is on screen and which account owns
 * each. SharedPreferences rather than DataStore, because the app reads it synchronously at start,
 * before it opens a database. Auto Backup includes it (`data_spaces.xml`), next to the databases.
 */
class SpaceRegistryStore(private val prefs: SharedPreferences) {

    fun load(): SpaceRegistry {
        val owners = prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(OWNER_PREFIX) && value is String) key.removePrefix(OWNER_PREFIX) to value else null
        }.toMap()
        return SpaceRegistry(prefs.getString(ACTIVE, FIRST_SPACE) ?: FIRST_SPACE, owners)
    }

    /** Written at once (`commit`), so a database is never opened for a space the file doesn't name. */
    fun save(registry: SpaceRegistry) {
        prefs.edit(commit = true) {
            clear()
            putString(ACTIVE, registry.active)
            registry.owners.forEach { (space, uid) -> putString(OWNER_PREFIX + space, uid) }
        }
    }

    /** A short id for a new space, not used by [registry]. */
    fun newSpaceId(registry: SpaceRegistry): String {
        while (true) {
            val id = UUID.randomUUID().toString().take(8)
            if (id != registry.active && id !in registry.owners) return id
        }
    }

    companion object {
        const val FILE = "data_spaces"
        private const val ACTIVE = "active"
        private const val OWNER_PREFIX = "owner:"
    }
}
