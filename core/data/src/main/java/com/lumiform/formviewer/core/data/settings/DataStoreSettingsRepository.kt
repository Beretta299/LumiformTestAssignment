package com.lumiform.formviewer.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lumiform.formviewer.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Preferences DataStore rather than Room: a display setting isn't form data, has no relations, and shouldn't share a
 * schema version (and migrations) with the form tables.
 */
internal class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val showItemIds: Flow<Boolean> =
            dataStore.data.map { it[SHOW_ITEM_IDS] ?: false }.distinctUntilChanged()

        override suspend fun setShowItemIds(show: Boolean) {
            dataStore.edit { it[SHOW_ITEM_IDS] = show }
        }

        private companion object {
            val SHOW_ITEM_IDS = booleanPreferencesKey("show_item_ids")
        }
    }
