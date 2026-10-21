package com.lumiform.formviewer.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConnectionSecurityRepository {
    /** True while the latest connection attempt had its certificate rejected, i.e. traffic may be intercepted. */
    val isCompromised: Flow<Boolean>
}
