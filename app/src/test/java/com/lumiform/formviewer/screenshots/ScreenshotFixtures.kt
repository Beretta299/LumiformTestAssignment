package com.lumiform.formviewer.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported
import com.lumiform.formviewer.ui.form.FormState
import com.lumiform.formviewer.ui.form.summary
import com.lumiform.formviewer.ui.form.toRows
import com.lumiform.formviewer.ui.theme.FormViewerTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** The sample endpoint's form, with one answer given and an item type this app version doesn't know. */
internal val sampleForm: List<FormItem> =
    listOf(
        Page(
            id = 1,
            title = "Main Page",
            children =
                listOf(
                    Section(
                        id = 2,
                        title = "Introduction",
                        children =
                            listOf(
                                TextQuestion(3, "Welcome to the main page!"),
                                ImageQuestion(4, "https://example.invalid/android.png", "Welcome Image"),
                            ),
                    ),
                    Section(
                        id = 5,
                        title = "Chapter 1",
                        children =
                            listOf(
                                TextQuestion(6, "This is the first chapter."),
                                Section(
                                    id = 7,
                                    title = "Subsection 1.1",
                                    children =
                                        listOf(
                                            TextQuestion(8, "This is a subsection under Chapter 1."),
                                            ImageQuestion(9, "https://example.invalid/ios.png", "Chapter 1 Image"),
                                        ),
                                ),
                            ),
                    ),
                ),
        ),
        Page(
            id = 10,
            title = "Second Page",
            children =
                listOf(
                    Section(
                        id = 11,
                        title = "Chapter 2",
                        children =
                            listOf(
                                TextQuestion(12, "This is the second chapter."),
                                ChoiceQuestion(
                                    id = 13,
                                    content = "What is the main topic of Chapter 2?",
                                    responseSet =
                                        ResponseSet(
                                            id = 101,
                                            multipleSelection = false,
                                            responses =
                                                listOf(
                                                    Response(1011, "Safety procedures", Score.Points(1)),
                                                    Response(1012, "Equipment checks", Score.Points(2)),
                                                    Response(1013, "Not applicable", Score.NotScored),
                                                ),
                                        ),
                                    selectedResponseIds = setOf(1012),
                                ),
                                ChoiceQuestion(
                                    id = 14,
                                    content = "Which areas were inspected?",
                                    responseSet =
                                        ResponseSet(
                                            id = 102,
                                            multipleSelection = true,
                                            responses =
                                                listOf(
                                                    Response(1021, "Entrance", Score.NotScored),
                                                    Response(1022, "Storage", Score.NotScored),
                                                    Response(1023, "Loading bay", Score.NotScored),
                                                ),
                                        ),
                                    selectedResponseIds = setOf(1021, 1023),
                                ),
                                Unsupported(15, rawType = "signature", children = emptyList()),
                            ),
                    ),
                ),
        ),
    )

/** "Five minutes ago" stays the same text however long the test takes. */
internal fun sampleContent(items: List<FormItem> = sampleForm) =
    FormState.Content(items.toRows(), items.summary(), lastSyncedAt = Clock.System.now() - 5.minutes)

/**
 * Renders like a preview: Coil's inspection-mode handler returns a fixed gradient instead of loading from the network,
 * so captures are deterministic. [fontScale] stands in for the system font size setting.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun ScreenshotHost(
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { sampleBitmap.asImage() },
        LocalDensity provides Density(density.density, fontScale),
    ) {
        FormViewerTheme(darkTheme = darkTheme, content = content)
    }
}

private val sampleBitmap: Bitmap by lazy {
    Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(0f, 0f, 800f, 600f, 0xFF3D7BF7.toInt(), 0xFF1E9E6A.toInt(), Shader.TileMode.CLAMP)
            }
        Canvas(bitmap).drawRect(0f, 0f, 800f, 600f, paint)
    }
}
