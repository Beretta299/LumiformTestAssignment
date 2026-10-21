package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.FakeFormRepository
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported
import com.lumiform.formviewer.core.domain.repository.ImageRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefetchFormImagesUseCaseTest {
    private val formRepository = FakeFormRepository()
    private val imageRepository = RecordingImageRepository()
    private val prefetch = PrefetchFormImagesUseCase(formRepository, imageRepository)

    @Test
    fun prefetchesEveryDistinctImageAtAnyDepth() =
        runTest {
            formRepository.form.value =
                listOf(
                    Page(
                        1,
                        "Page",
                        listOf(
                            ImageQuestion(2, "https://example.com/a.png", "A"),
                            Section(
                                3,
                                "Section",
                                listOf(
                                    Section(4, "Nested", listOf(ImageQuestion(5, "https://example.com/b.png", "B"))),
                                ),
                            ),
                            Unsupported(6, "gallery", listOf(ImageQuestion(7, "https://example.com/a.png", "A again"))),
                            TextQuestion(8, "Text"),
                        ),
                    ),
                )

            assertTrue(prefetch())
            assertEquals(setOf("https://example.com/a.png", "https://example.com/b.png"), imageRepository.requested)
        }

    @Test
    fun imagesWithoutSource_areSkipped() =
        runTest {
            formRepository.form.value = listOf(ImageQuestion(1, null, "No src"), ImageQuestion(2, " ", "Blank"))

            assertTrue(prefetch())
            assertNull(imageRepository.requested)
        }

    @Test
    fun failedDownload_isReported() =
        runTest {
            formRepository.form.value = listOf(ImageQuestion(1, "https://example.com/a.png", "A"))
            imageRepository.succeeds = false

            assertFalse(prefetch())
        }
}

private class RecordingImageRepository : ImageRepository {
    var requested: Set<String>? = null
    var succeeds = true

    override suspend fun prefetch(urls: Set<String>): Boolean {
        requested = urls
        return succeeds
    }
}
