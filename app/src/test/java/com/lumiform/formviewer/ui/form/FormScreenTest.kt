package com.lumiform.formviewer.ui.form

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.R
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormItem
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A plain Application keeps the real one's WorkManager and Hilt setup out; a phone-sized screen fits the form. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class FormScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val choice =
        ChoiceQuestion(
            id = 4,
            content = "Is it clean?",
            responseSet =
                ResponseSet(
                    id = 40,
                    multipleSelection = false,
                    responses = listOf(Response(41, "Yes", Score.Points(1)), Response(42, "No", Score.NotScored)),
                ),
            selectedResponseIds = setOf(41),
        )
    private val form =
        listOf(
            Page(
                id = 1,
                title = "Inspection",
                children =
                    listOf(
                        Section(
                            id = 2,
                            title = "Kitchen",
                            children = listOf(TextQuestion(3, "Check the floor"), choice),
                        ),
                    ),
            ),
        )

    private var refreshCount = 0
    private var updateAppCount = 0
    private var refreshFailureShownCount = 0
    private val clickedResponses = mutableListOf<Pair<Long, Long>>()
    private val clickedImages = mutableListOf<Long>()
    private val showItemIdsChanges = mutableListOf<Boolean>()

    private fun string(
        @StringRes id: Int,
    ) = application.getString(id)

    private fun content(items: List<FormItem> = form) =
        FormState.Content(items.toRows(), items.summary(), lastSyncedAt = null)

    private fun setScreen(
        state: FormUiState,
        canUpdateApp: Boolean = false,
    ) {
        composeRule.setContent {
            FormViewerTheme {
                FormScreen(
                    state = state,
                    layout = FormLayout.Compact,
                    onRefresh = { refreshCount++ },
                    onRefreshFailureShown = { refreshFailureShownCount++ },
                    onResponseClick = { question, responseId -> clickedResponses += question.id to responseId },
                    onUpdateApp = if (canUpdateApp) ({ updateAppCount++ }) else null,
                    onImageClick = { clickedImages += it },
                    onShowItemIdsChange = { showItemIdsChanges += it },
                )
            }
        }
    }

    @Test
    fun content_showsEveryLevelOfTheForm() {
        setScreen(FormUiState(form = content()))

        listOf("Inspection", "Kitchen", "Check the floor", "Is it clean?", "Yes", "No").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun choice_showsTheStoredSelectionAndScores() {
        setScreen(FormUiState(form = content()))

        composeRule.onNodeWithText("Yes").assertIsSelected()
        composeRule.onNodeWithText("No").assertIsNotSelected()
        composeRule
            .onNodeWithContentDescription(
                application.getString(R.string.form_score, 1),
                substring = true,
            ).assertExists()
    }

    @Test
    fun choice_tapReportsTheQuestionAndResponse() {
        setScreen(FormUiState(form = content()))

        composeRule.onNodeWithText("No").performClick()
        composeRule.onNodeWithText("Yes").performClick()

        assertThat(clickedResponses).containsExactly(4L to 42L, 4L to 41L).inOrder()
    }

    @Test
    fun image_tapOpensTheViewerForThatQuestion() {
        val image =
            listOf(
                Page(
                    id = 1,
                    title = "Inspection",
                    children = listOf(ImageQuestion(5, "https://example.invalid/a.png", "Fridge")),
                ),
            )
        setScreen(FormUiState(form = content(image)))

        composeRule.onNodeWithContentDescription("Fridge", substring = true).performClick()

        assertThat(clickedImages).containsExactly(5L)
    }

    @Test
    fun image_withoutASource_saysSo_andDoesNotOpen() {
        val image = listOf(Page(id = 1, title = "Inspection", children = listOf(ImageQuestion(5, null, "Fridge"))))
        setScreen(FormUiState(form = content(image)))

        composeRule.onNodeWithText(string(R.string.form_image_missing)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fridge", substring = true).performClick()

        assertThat(clickedImages).isEmpty()
    }

    @Test
    fun itemIds_areHiddenByDefault_andShownWhenTheSettingIsOn() {
        var state by mutableStateOf(FormUiState(form = content()))
        composeRule.setContent {
            FormViewerTheme {
                FormScreen(state, FormLayout.Compact, onRefresh = {}, onRefreshFailureShown = {}, onResponseClick = {
                    _,
                    _,
                    ->
                })
            }
        }
        composeRule.onNodeWithText("#3").assertDoesNotExist()

        state = state.copy(showItemIds = true)

        listOf("#1", "#2", "#3", "#4").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun settings_openFromTheHeader_andToggleItemIds() {
        setScreen(FormUiState(form = content()))

        composeRule.onNodeWithContentDescription(string(R.string.form_settings)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_show_item_ids)).assertIsDisplayed().performClick()

        assertThat(showItemIdsChanges).containsExactly(true)
    }

    @Test
    fun header_showsAnswerProgress_andRefreshes() {
        setScreen(FormUiState(form = content()))

        composeRule.onNodeWithText("1/1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.form_refresh)).performClick()

        assertThat(refreshCount).isEqualTo(1)
    }

    @Test
    fun header_showsOfflineStatus_whenOffline() {
        setScreen(FormUiState(form = content(), isOffline = true))

        composeRule.onNodeWithText(string(R.string.form_offline_banner_undated)).assertIsDisplayed()
    }

    @Test
    fun content_showsAnUpdateCard_forUnsupportedItems() {
        val unsupported = listOf(Unsupported(id = 9, rawType = "wizard", children = emptyList()))
        setScreen(FormUiState(form = content(unsupported)), canUpdateApp = true)

        composeRule.onNodeWithText(string(R.string.form_unsupported_item)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.form_unsupported_update)).performClick()

        assertThat(updateAppCount).isEqualTo(1)
    }

    @Test
    fun content_hidesTheUpdateAction_whenNoStoreCanOpenIt() {
        setScreen(FormUiState(form = content(listOf(Unsupported(id = 9, rawType = "wizard", children = emptyList())))))

        composeRule.onNodeWithText(string(R.string.form_unsupported_update)).assertDoesNotExist()
    }

    @Test
    fun error_retries() {
        setScreen(FormUiState(form = FormState.Error(SyncError.Offline)))

        composeRule.onNodeWithText(string(R.string.form_error_offline_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.form_retry)).performClick()

        assertThat(refreshCount).isEqualTo(1)
    }

    @Test
    fun empty_explainsThatTheFormIsEmpty() {
        setScreen(FormUiState(form = FormState.Empty))

        composeRule.onNodeWithText(string(R.string.form_empty_title)).assertIsDisplayed()
    }

    @Test
    fun insecureConnection_hidesTheFormBehindABlocker() {
        setScreen(FormUiState(form = content(), isConnectionInsecure = true))

        composeRule.onNodeWithText(string(R.string.form_error_insecure_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.form_retry)).performClick()
        assertThat(refreshCount).isEqualTo(1)
    }

    @Test
    fun refreshFailure_isShownInASnackbarOnce() {
        setScreen(FormUiState(form = content(), refreshFailure = SyncError.Server))

        composeRule.onNodeWithText(string(R.string.form_refresh_failed_server)).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(SNACKBAR_TIMEOUT_MILLIS)
        composeRule.waitForIdle()

        assertThat(refreshFailureShownCount).isEqualTo(1)
    }

    private companion object {
        const val SNACKBAR_TIMEOUT_MILLIS = 10_000L
    }
}
