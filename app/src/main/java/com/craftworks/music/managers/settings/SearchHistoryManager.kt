package com.craftworks.music.managers.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val MAX_RECENT_SEARCHES = 8

internal fun updatedRecentSearches(
    existing: List<String>,
    query: String,
    limit: Int = MAX_RECENT_SEARCHES
): List<String> {
    val normalized = query.trim().replace(Regex("\\s+"), " ")
    if (normalized.isEmpty()) return existing.take(limit)
    return buildList {
        add(normalized)
        existing.filterNot { it.equals(normalized, ignoreCase = true) }.forEach(::add)
    }.take(limit)
}

class SearchHistoryManager(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    val recentSearchesFlow: Flow<List<String>> = appContext.dataStore.data.map { preferences ->
        preferences[RECENT_SEARCHES]
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString(ListSerializer(String.serializer()), encoded)
                }.getOrDefault(emptyList())
            }
            .orEmpty()
    }

    suspend fun recordSearch(query: String) {
        appContext.dataStore.edit { preferences ->
            val existing = preferences[RECENT_SEARCHES]
                ?.let { encoded ->
                    runCatching {
                        json.decodeFromString(ListSerializer(String.serializer()), encoded)
                    }.getOrDefault(emptyList())
                }
                .orEmpty()
            preferences[RECENT_SEARCHES] = json.encodeToString(
                ListSerializer(String.serializer()),
                updatedRecentSearches(existing, query)
            )
        }
    }

    suspend fun clear() {
        appContext.dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    private companion object {
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }
}
