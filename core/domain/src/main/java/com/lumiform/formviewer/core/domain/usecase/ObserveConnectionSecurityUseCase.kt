package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.repository.ConnectionSecurityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits whether the connection is compromised (a certificate was rejected). */
class ObserveConnectionSecurityUseCase
    @Inject
    constructor(
        private val repository: ConnectionSecurityRepository,
    ) {
        operator fun invoke(): Flow<Boolean> = repository.isCompromised
    }
