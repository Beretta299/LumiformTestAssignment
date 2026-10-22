package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveShowItemIdsUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        operator fun invoke(): Flow<Boolean> = repository.showItemIds
    }
