package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.FormContainer
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.repository.FormRepository
import com.lumiform.formviewer.core.domain.repository.ImageRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Caches every image of the stored form, so images the user hasn't scrolled to yet still show offline. */
class PrefetchFormImagesUseCase
    @Inject
    constructor(
        private val formRepository: FormRepository,
        private val imageRepository: ImageRepository,
    ) {
        /** Returns false if any image failed to download. */
        suspend operator fun invoke(): Boolean {
            val urls =
                formRepository
                    .observeForm()
                    .first()
                    .flatMap { it.selfAndDescendants() }
                    .filterIsInstance<ImageQuestion>()
                    .mapNotNullTo(mutableSetOf()) { it.src?.takeIf(String::isNotBlank) }
            return urls.isEmpty() || imageRepository.prefetch(urls)
        }
    }

private fun FormItem.selfAndDescendants(): List<FormItem> =
    listOf(this) + (this as? FormContainer)?.children?.flatMap { it.selfAndDescendants() }.orEmpty()
