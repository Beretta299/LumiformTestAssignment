package com.lumiform.formviewer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConnectivityRepository {
    /** True while the device has a network that can reach the internet. */
    val isOnline: Flow<Boolean>
}
