package com.lumiform.formviewer.ui.image

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.R
import com.lumiform.formviewer.ui.theme.FormViewerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Images don't load under Robolectric, so these cover the chrome and gestures around the (failed) image. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class ImageViewerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val content =
        ImageViewerUiState.Content(
            questionId = 7,
            src = "https://example.invalid/a.png",
            title = "Fridge",
        )
    private var dismissCount = 0

    private fun string(
        @StringRes id: Int,
    ) = application.getString(id)

    private fun setScreen(state: ImageViewerUiState = content) {
        composeRule.setContent {
            FormViewerTheme { ImageViewerScreen(state, onDismiss = { dismissCount++ }) }
        }
    }

    private fun image() = composeRule.onNodeWithContentDescription("Fridge")

    @Test
    fun showsTheTitle_andClosesFromTheButton() {
        setScreen()

        composeRule.onNodeWithText("Fridge").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.image_viewer_close)).performClick()

        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun doubleTap_zooms_andHidesTheTitle() {
        setScreen()

        // Above the centre, where the failed image's Retry button sits.
        image().performTouchInput { doubleClick(Offset(centerX, height * 0.2f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Fridge").assertDoesNotExist()
        assertThat(dismissCount).isEqualTo(0)
    }

    @Test
    fun swipeDown_dismisses() {
        setScreen()

        image().performTouchInput { swipeDown() }

        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun pinchingOut_dismisses() {
        setScreen()

        image().performTouchInput {
            pinch(
                start0 = center - Offset(width * 0.4f, 0f),
                end0 = center - Offset(width * 0.05f, 0f),
                start1 = center + Offset(width * 0.4f, 0f),
                end1 = center + Offset(width * 0.05f, 0f),
            )
        }

        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun missingQuestion_explainsWhy() {
        setScreen(ImageViewerUiState.NotFound)

        composeRule.onNodeWithText(string(R.string.image_viewer_not_found)).assertIsDisplayed()
    }

    @Test
    fun missingSource_explainsWhy() {
        setScreen(content.copy(src = null))

        composeRule.onNodeWithText(string(R.string.image_viewer_no_image)).assertIsDisplayed()
    }
}
