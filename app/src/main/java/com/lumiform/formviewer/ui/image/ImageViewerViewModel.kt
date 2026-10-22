package com.lumiform.formviewer.ui.image

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lumiform.formviewer.core.domain.usecase.GetImageQuestionUseCase
import com.lumiform.formviewer.ui.navigation.ImageDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImageViewerUiState {
    data object Loading : ImageViewerUiState

    data class Content(
        val questionId: Long,
        val src: String?,
        val title: String?,
    ) : ImageViewerUiState

    /** The question is gone, e.g. a sync removed it while the route was restored after process death. */
    data object NotFound : ImageViewerUiState
}

@HiltViewModel
class ImageViewerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getImageQuestion: GetImageQuestionUseCase,
    ) : ViewModel() {
        private val questionId = savedStateHandle.toRoute<ImageDestination>().questionId
        private val _uiState = MutableStateFlow<ImageViewerUiState>(ImageViewerUiState.Loading)
        val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                _uiState.value =
                    getImageQuestion(questionId)
                        ?.let { ImageViewerUiState.Content(it.id, it.src, it.title) }
                        ?: ImageViewerUiState.NotFound
            }
        }
    }
