package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.repository.FormRepository
import javax.inject.Inject

class GetImageQuestionUseCase
    @Inject
    constructor(
        private val repository: FormRepository,
    ) {
        suspend operator fun invoke(id: Long): ImageQuestion? = repository.getImageQuestion(id)
    }
