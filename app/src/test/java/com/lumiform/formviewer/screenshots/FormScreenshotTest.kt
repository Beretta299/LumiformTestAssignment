package com.lumiform.formviewer.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.ui.form.FormScreen
import com.lumiform.formviewer.ui.form.FormState
import com.lumiform.formviewer.ui.form.FormUiState
import com.lumiform.formviewer.ui.form.currentFormLayout
import com.lumiform.formviewer.ui.image.ImageViewerScreen
import com.lumiform.formviewer.ui.image.ImageViewerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screenshot matrix: light/dark, font scale 1.0/1.5/2.0, and five window sizes (small phone, phone, foldable,
 * tablet, phone landscape). Images live in `src/test/screenshots` and are committed: `recordRoborazziDebug` writes
 * them, `verifyRoborazziDebug` (run in CI) fails when an image changes. A plain `testDebugUnitTest` skips the capture.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = PHONE)
class FormScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        state: FormUiState = FormUiState(form = sampleContent()),
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        scrollTo: String? = null,
    ) {
        val content = @Composable {
            ScreenshotHost(darkTheme, fontScale) {
                FormScreen(
                    state = state,
                    layout = currentFormLayout(),
                    onRefresh = {},
                    onRefreshFailureShown = {},
                    onResponseClick = { _, _ -> },
                )
            }
        }
        composeRule.setContent(content)
        scrollTo?.let { composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it)) }
        captureScreen(name)
    }

    /**
     * A still screen (no list, nothing animating) was now and then captured before its first frame was recorded, as a
     * blank image. Invalidating the root and waiting for idle forces that frame.
     */
    private fun captureScreen(name: String) {
        val root = composeRule.onRoot().fetchSemanticsNode().root as ViewRootForTest
        composeRule.runOnUiThread { root.view.invalidate() }
        composeRule.waitForIdle()
        composeRule
            .onRoot()
            .captureToImage()
            .asAndroidBitmap()
            .captureRoboImage("$SCREENSHOT_DIR/$name.png", screenshotOptions)
    }

    @Test
    fun phone_light() = capture("form_phone_light")

    /** Scrolled, so the header shows collapsed, with the choice questions on screen. */
    @Test
    fun phone_choices() = capture("form_phone_choices", scrollTo = "Which areas were inspected?")

    @Test
    fun phone_choices_dark() =
        capture("form_phone_choices_dark", darkTheme = true, scrollTo = "Which areas were inspected?")

    @Test
    fun phone_itemIds() = capture("form_phone_item_ids", FormUiState(form = sampleContent(), showItemIds = true))

    @Test
    fun phone_dark() = capture("form_phone_dark", darkTheme = true)

    @Test
    fun phone_fontScale150() = capture("form_phone_font150", fontScale = 1.5f)

    @Test
    fun phone_fontScale200() = capture("form_phone_font200", fontScale = 2f)

    @Test
    fun phone_dark_fontScale200() = capture("form_phone_dark_font200", darkTheme = true, fontScale = 2f)

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun smallPhone_light() = capture("form_small_phone_light")

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun phoneLandscape_light() = capture("form_phone_landscape_light")

    @Test
    @Config(qualifiers = FOLDABLE)
    fun foldable_light() = capture("form_foldable_light")

    @Test
    @Config(qualifiers = TABLET)
    fun tablet_light() = capture("form_tablet_light")

    @Test
    @Config(qualifiers = TABLET)
    fun tablet_dark() = capture("form_tablet_dark", darkTheme = true)

    @Test
    fun phone_offline() = capture("form_phone_offline", FormUiState(form = sampleContent(), isOffline = true))

    @Test
    fun phone_empty() = capture("form_phone_empty", FormUiState(form = FormState.Empty))

    @Test
    fun phone_errorOffline() =
        capture("form_phone_error_offline", FormUiState(form = FormState.Error(SyncError.Offline)))

    @Test
    fun phone_insecureConnection() =
        capture("form_phone_insecure", FormUiState(form = sampleContent(), isConnectionInsecure = true))

    @Test
    fun imageViewer_phone() {
        composeRule.setContent {
            ScreenshotHost {
                ImageViewerScreen(
                    ImageViewerUiState.Content(4, "https://example.invalid/android.png", "Welcome Image"),
                    onDismiss = {},
                )
            }
        }
        captureScreen("image_viewer_phone")
    }
}

/**
 * Baselines are recorded on macOS and verified on Linux in CI; text anti-aliasing differs slightly between the two, so
 * a tiny share of changed pixels is tolerated. A real layout or colour change touches far more than this.
 */
private val screenshotOptions =
    RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

internal const val SCREENSHOT_DIR = "src/test/screenshots"
internal const val SMALL_PHONE = "w360dp-h640dp-xhdpi"
internal const val PHONE = "w411dp-h914dp-xhdpi"
internal const val PHONE_LANDSCAPE = "w914dp-h411dp-land-xhdpi"
internal const val FOLDABLE = "w673dp-h841dp-xhdpi"
internal const val TABLET = "w1280dp-h800dp-land-mdpi"
