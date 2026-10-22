package com.lumiform.formviewer.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumiform.formviewer.ui.form.FormRoute
import com.lumiform.formviewer.ui.image.ImageViewerRoute

/**
 * The image viewer is a real destination, as the brief asks for a new screen, but feels like a pop-up: the form
 * stays put underneath while the viewer fades in and the thumbnail grows into the full-size image.
 */
@Composable
fun FormViewerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = FormDestination) {
                composable<FormDestination>(
                    exitTransition = { fadeOut(targetAlpha = 1f) },
                    popEnterTransition = { fadeIn(initialAlpha = 1f) },
                ) { entry ->
                    ProvideVisibilityScope {
                        FormRoute(onImageClick = { id ->
                            entry.ifResumed { navController.navigate(ImageDestination(id)) }
                        })
                    }
                }
                composable<ImageDestination>(
                    enterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() },
                ) { entry ->
                    ProvideVisibilityScope {
                        ImageViewerRoute(onDismiss = { entry.ifResumed { navController.popBackStack() } })
                    }
                }
            }
        }
    }
}

/** Drops a second tap or gesture that lands while a transition is already leaving this destination. */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action()
}

@Composable
private fun AnimatedContentScope.ProvideVisibilityScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}
