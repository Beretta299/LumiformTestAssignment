package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.repository.FormRepository
import javax.inject.Inject

/** The one entry point for every sync trigger (launch, pull-to-refresh, retry, background work). */
class SyncFormUseCase
    @Inject
    constructor(
        private val repository: FormRepository,
    ) {
        suspend operator fun invoke(): SyncResult = repository.sync()
    }
