package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.repository.FormRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFormUseCase
    @Inject
    constructor(
        private val repository: FormRepository,
    ) {
        operator fun invoke(): Flow<List<FormItem>> = repository.observeForm()
    }
