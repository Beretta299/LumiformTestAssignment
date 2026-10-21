package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.FormRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSyncStateUseCase
    @Inject
    constructor(
        private val repository: FormRepository,
    ) {
        operator fun invoke(): Flow<SyncState> = repository.observeSyncState()
    }
