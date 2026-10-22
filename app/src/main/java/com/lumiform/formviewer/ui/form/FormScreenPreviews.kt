package com.lumiform.formviewer.ui.form

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported
import com.lumiform.formviewer.ui.theme.FormViewerTheme
import kotlin.time.Instant

private fun previewChoice(
    id: Long,
    multipleSelection: Boolean,
    selected: Set<Long>,
) = ChoiceQuestion(
    id = id,
    content = if (multipleSelection) "Which areas were checked?" else "Is the area clean?",
    responseSet =
        ResponseSet(
            id = id * 10,
            multipleSelection = multipleSelection,
            responses =
                listOf(
                    Response(id * 10 + 1, "Yes", Score.Points(1)),
                    Response(id * 10 + 2, "No", Score.Points(0)),
                    Response(id * 10 + 3, "Not applicable", Score.NotScored),
                ),
        ),
    selectedResponseIds = selected,
)

private val previewForm =
    listOf(
        Page(
            id = 1,
            title = "Kitchen inspection",
            children =
                listOf(
                    Section(
                        id = 2,
                        title = "Chapter 1",
                        children =
                            listOf(
                                TextQuestion(3, "Walk through the kitchen before service."),
                                Section(
                                    id = 4,
                                    title = "Subsection 1.1",
                                    children =
                                        listOf(
                                            previewChoice(5, multipleSelection = false, selected = setOf(51)),
                                            previewChoice(6, multipleSelection = true, selected = setOf(61, 63)),
                                            ImageQuestion(7, src = null, title = "Fridge seal"),
                                        ),
                                ),
                            ),
                    ),
                    Unsupported(8, rawType = "signature", children = emptyList()),
                ),
        ),
    )

private val previewContent =
    FormState.Content(
        previewForm.toRows(),
        previewForm.summary(),
        lastSyncedAt = Instant.fromEpochMilliseconds(0),
    )

private class FormUiStateProvider : PreviewParameterProvider<FormUiState> {
    override val values =
        sequenceOf(
            FormUiState(form = previewContent),
            FormUiState(form = previewContent, isOffline = true),
            FormUiState(form = FormState.Loading),
            FormUiState(form = FormState.Empty),
            FormUiState(form = FormState.Error(SyncError.Offline)),
            FormUiState(
                form = previewContent,
                isConnectionInsecure = true,
            ),
        )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FormScreenPreview(
    @PreviewParameter(FormUiStateProvider::class) state: FormUiState,
) {
    FormViewerTheme {
        FormScreen(
            state = state,
            layout = FormLayout.Compact,
            onRefresh = {},
            onRefreshFailureShown = {},
            onResponseClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun FormScreenWidePreview() {
    FormViewerTheme {
        FormScreen(
            state = FormUiState(form = previewContent),
            layout = FormLayout.Wide,
            onRefresh = {},
            onRefreshFailureShown = {},
            onResponseClick = { _, _ -> },
        )
    }
}
