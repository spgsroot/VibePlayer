package ru.spgsroot.peachy.data.manga

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.peachy.manga.TagLibrary
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tagDataStore by preferencesDataStore("manga_tags")

/** Persists tag favorites and collections as one JSON document in DataStore. */
@Singleton
class TagPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val libraryKey = stringPreferencesKey("library")

    val library: Flow<TagLibrary> = context.tagDataStore.data.map { TagLibrary.fromJson(it[libraryKey]) }

    /** Applies [change] to the stored library atomically, so quick successive edits cannot overwrite each other. */
    suspend fun update(change: (TagLibrary) -> TagLibrary) {
        context.tagDataStore.edit { prefs ->
            prefs[libraryKey] = change(TagLibrary.fromJson(prefs[libraryKey])).toJson()
        }
    }
}
