package com.kevin.inventorypurchases.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "group_session")

class GroupSession(private val context: Context) {
    private val KEY_ACTIVE = stringPreferencesKey("active_group")
    private val KEY_RECENTS = stringSetPreferencesKey("recent_groups")

    val activeGroupName: Flow<String?> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_ACTIVE] }

    val recentGroups: Flow<List<String>> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> (prefs[KEY_RECENTS] ?: emptySet()).toList().sorted() }

    suspend fun setActiveGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = trimmed
            val set = prefs[KEY_RECENTS]?.toMutableSet() ?: mutableSetOf()
            set.add(trimmed)
            if (set.size > 12) set.remove(set.first())
            prefs[KEY_RECENTS] = set
        }
    }

    suspend fun clearActiveGroup() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_ACTIVE) }
    }
}
