package com.lumiform.formviewer.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** The user's display preferences, kept on the device. */
interface SettingsRepository {
    /** Whether each item shows its raw id; off by default, since ids mean nothing to a person filling in the form. */
    val showItemIds: Flow<Boolean>

    suspend fun setShowItemIds(show: Boolean)
}
