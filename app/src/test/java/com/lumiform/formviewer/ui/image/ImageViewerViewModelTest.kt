package com.lumiform.formviewer.ui.image

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.usecase.GetImageQuestionUseCase
import com.lumiform.formviewer.ui.form.FakeFormRepository
import com.lumiform.formviewer.ui.navigation.ImageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric, because the route is read from a SavedStateHandle backed by an Android Bundle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class ImageViewerViewModelTest {
    private val repository = FakeFormRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(questionId: Long) =
        ImageViewerViewModel(
            savedStateHandle = SavedStateHandle(route = ImageDestination(questionId)),
            getImageQuestion = GetImageQuestionUseCase(repository),
        )

    @Test
    fun loadsTheQuestionNamedByTheRoute() {
        repository.imageQuestions[7] = ImageQuestion(id = 7, src = "https://example.com/a.png", title = "Fridge")

        assertThat(viewModel(7).uiState.value)
            .isEqualTo(ImageViewerUiState.Content(questionId = 7, src = "https://example.com/a.png", title = "Fridge"))
    }

    @Test
    fun reportsNotFound_whenTheQuestionIsGone() {
        assertThat(viewModel(7).uiState.value).isEqualTo(ImageViewerUiState.NotFound)
    }
}
