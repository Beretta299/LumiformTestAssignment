package com.lumiform.formviewer.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The scopes a shared element needs, provided by the nav host so the thumbnail deep inside the form's rows doesn't
 * thread them through every composable. Null outside the nav host (previews, tests of a single screen), where
 * [sharedImage] simply does nothing.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Links an image question's thumbnail and the viewer's full-size image, so one grows into the other. */
@Composable
fun Modifier.sharedImage(questionId: Long): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(transitionScope) {
        this@sharedImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "image-$questionId"),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
