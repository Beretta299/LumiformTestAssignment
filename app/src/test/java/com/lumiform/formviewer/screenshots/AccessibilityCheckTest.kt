package com.lumiform.formviewer.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.ui.form.FormLayout
import com.lumiform.formviewer.ui.form.FormScreen
import com.lumiform.formviewer.ui.form.FormState
import com.lumiform.formviewer.ui.form.FormUiState
import com.lumiform.formviewer.ui.image.ImageViewerScreen
import com.lumiform.formviewer.ui.image.ImageViewerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs Google's Accessibility Test Framework over each screen: touch target size, colour contrast (from the rendered
 * pixels, hence native graphics), missing or duplicate labels, and clickable items without a name. Any warning fails.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = PHONE)
class AccessibilityCheckTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options =
        RoborazziATFAccessibilityCheckOptions(
            checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST),
            failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning,
        )

    private fun checkForm(
        state: FormUiState,
        darkTheme: Boolean = false,
        scrollTo: String? = null,
    ) {
        composeRule.setContent {
            ScreenshotHost(darkTheme) {
                FormScreen(
                    state = state,
                    layout = FormLayout.Compact,
                    onRefresh = {},
                    onRefreshFailureShown = {},
                    onResponseClick = { _, _ -> },
                    onUpdateApp = {},
                )
            }
        }
        scrollTo?.let { composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it)) }
        composeRule.onRoot().checkRoboAccessibility(options)
    }

    @Test
    fun form_light() = checkForm(FormUiState(form = sampleContent()))

    @Test
    fun form_dark() = checkForm(FormUiState(form = sampleContent()), darkTheme = true)

    /** The checks only see what is on screen, so the choice questions get their own pass. */
    @Test
    fun form_choices() = checkForm(FormUiState(form = sampleContent()), scrollTo = "Which areas were inspected?")

    @Test
    fun form_choices_dark() =
        checkForm(FormUiState(form = sampleContent()), darkTheme = true, scrollTo = "Which areas were inspected?")

    @Test
    fun form_offline() = checkForm(FormUiState(form = sampleContent(), isOffline = true))

    @Test
    fun error() = checkForm(FormUiState(form = FormState.Error(SyncError.Server)))

    @Test
    fun insecureConnection() = checkForm(FormUiState(form = sampleContent(), isConnectionInsecure = true))

    @Test
    fun imageViewer() {
        composeRule.setContent {
            ScreenshotHost {
                ImageViewerScreen(
                    ImageViewerUiState.Content(
                        questionId = 4,
                        src = "https://example.invalid/android.png",
                        title = "Welcome Image",
                    ),
                    onDismiss = {},
                )
            }
        }
        composeRule.onRoot().checkRoboAccessibility(options)
    }
}
