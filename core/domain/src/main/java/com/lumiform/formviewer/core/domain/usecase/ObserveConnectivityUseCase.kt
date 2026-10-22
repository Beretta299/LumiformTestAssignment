package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.repository.ConnectivityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits whether the device is online. */
class ObserveConnectivityUseCase
    @Inject
    constructor(
        private val repository: ConnectivityRepository,
    ) {
        operator fun invoke(): Flow<Boolean> = repository.isOnline
    }
