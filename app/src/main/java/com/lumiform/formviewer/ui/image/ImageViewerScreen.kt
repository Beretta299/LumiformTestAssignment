package com.lumiform.formviewer.ui.image

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.lumiform.formviewer.R
import com.lumiform.formviewer.ui.form.thumbnailMemoryCacheKey
import com.lumiform.formviewer.ui.navigation.sharedImage
import com.lumiform.formviewer.ui.theme.FormTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.util.lerp as lerpFloat
import coil3.size.Size as CoilSize

@Composable
fun ImageViewerRoute(
    onDismiss: () -> Unit,
    viewModel: ImageViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImageViewerScreen(state, onDismiss)
}

/**
 * Full-size image and its title on a dark scrim. Double-tap zooms, pinch zooms and pans; pinching below 1× or swiping
 * an unzoomed image away closes it, as do the close button and system back. The title hides while zoomed.
 */
@Composable
fun ImageViewerScreen(
    state: ImageViewerUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    LightSystemBarIcons()
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    var isZoomed by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.hero.copy(alpha = 1f - dismissProgress * 0.85f)),
    ) {
        when (state) {
            ImageViewerUiState.Loading -> {
                Unit
            }

            ImageViewerUiState.NotFound -> {
                ViewerMessage(R.string.image_viewer_not_found)
            }

            is ImageViewerUiState.Content -> {
                val src = state.src
                if (src == null) {
                    ViewerMessage(R.string.image_viewer_no_image)
                } else {
                    ViewerImage(
                        src = src,
                        title = state.title,
                        questionId = state.questionId,
                        onDismiss = onDismiss,
                        onTransformChanged = { transform, progress ->
                            isZoomed = transform.isZoomed
                            dismissProgress = progress
                        },
                    )
                }
            }
        }
        val chromeVisible = !isZoomed && dismissProgress == 0f
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding(),
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.padding(4.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.image_viewer_close),
                    tint = colors.onHero,
                )
            }
        }
        val title = (state as? ImageViewerUiState.Content)?.title
        AnimatedVisibility(
            visible = chromeVisible && title != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onHero,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .background(colors.hero.copy(alpha = 0.6f))
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .semantics { heading() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerImage(
    src: String,
    title: String?,
    questionId: Long,
    onDismiss: () -> Unit,
    onTransformChanged: (ZoomTransform, dismissProgress: Float) -> Unit,
) {
    val colors = FormTheme.colors
    val context = LocalContext.current
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val request =
        remember(src, attempt) {
            ImageRequest
                .Builder(context)
                .data(src)
                // The full-size image the brief asks for; zooming needs the detail a screen-sized decode would drop.
                .size(CoilSize.ORIGINAL)
                // Shows the thumbnail already in memory while the full image loads, so the transition lands on a picture.
                .placeholderMemoryCacheKey(MemoryCache.Key(thumbnailMemoryCacheKey(src)))
                .build()
        }
    val painter = rememberAsyncImagePainter(request)
    val painterState by painter.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ZoomableImage(
            painter = painter,
            contentDescription = title ?: stringResource(R.string.form_image_untitled),
            questionId = questionId,
            onDismiss = onDismiss,
            onTransformChanged = onTransformChanged,
        )
        when (val current = painterState) {
            is AsyncImagePainter.State.Loading -> {
                if (current.painter == null) {
                    LoadingIndicator(color = colors.signal, modifier = Modifier.size(56.dp))
                }
            }

            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        stringResource(R.string.image_viewer_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onHero,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { attempt++ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onHero),
                    ) {
                        Text(stringResource(R.string.form_retry))
                    }
                }
            }

            else -> {
                Unit
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    painter: AsyncImagePainter,
    contentDescription: String,
    questionId: Long,
    onDismiss: () -> Unit,
    onTransformChanged: (ZoomTransform, dismissProgress: Float) -> Unit,
) {
    var transform by remember { mutableStateOf(ZoomTransform()) }
    var container by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun update(new: ZoomTransform) {
        transform = new
        onTransformChanged(new, new.dismissProgress(container))
    }

    fun settleTo(target: ZoomTransform) {
        val from = transform
        settleJob?.cancel()
        settleJob = scope.launch { animate(0f, 1f) { t, _ -> update(lerp(from, target, t)) } }
    }

    val zoomInLabel = stringResource(R.string.image_viewer_zoom_in)
    val zoomOutLabel = stringResource(R.string.image_viewer_zoom_out)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { container = it.toSize() }
                // Gestures read untransformed coordinates, so they sit outside the graphics layer.
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { settleTo(transform.toggleZoom(it, container)) })
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        settleJob?.cancel()
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                update(transform.applyGesture(event.calculateCentroid(), pan, zoom, container))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        when (val end = transform.onGestureEnd(container)) {
                            GestureEnd.Dismiss -> currentOnDismiss()
                            is GestureEnd.SettleTo -> if (end.transform != transform) settleTo(end.transform)
                        }
                    }
                }.semantics {
                    // Gestures aren't reachable with TalkBack or switch access, so zoom is also an action.
                    customActions =
                        listOf(
                            CustomAccessibilityAction(if (transform.isZoomed) zoomOutLabel else zoomInLabel) {
                                settleTo(transform.toggleZoom(container.centerOffset(), container))
                                true
                            },
                        )
                }.sharedImage(questionId)
                .graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offset.x
                    translationY = transform.offset.y + transform.dismissDrag
                },
    )
}

@Composable
private fun ViewerMessage(
    @StringRes message: Int,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = FormTheme.colors.onHero,
            textAlign = TextAlign.Center,
        )
    }
}

/** The scrim is dark in both themes, so the status bar icons turn light while the viewer is shown. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
}

private fun lerp(
    from: ZoomTransform,
    to: ZoomTransform,
    fraction: Float,
) = ZoomTransform(
    scale = lerpFloat(from.scale, to.scale, fraction),
    offset = lerp(from.offset, to.offset, fraction),
    dismissDrag = lerpFloat(from.dismissDrag, to.dismissDrag, fraction),
)

private fun Size.centerOffset() = Offset(width / 2f, height / 2f)
