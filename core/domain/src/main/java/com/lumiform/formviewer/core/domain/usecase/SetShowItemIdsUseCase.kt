package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.repository.SettingsRepository
import javax.inject.Inject

class SetShowItemIdsUseCase
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) {
        suspend operator fun invoke(show: Boolean) = repository.setShowItemIds(show)
    }
