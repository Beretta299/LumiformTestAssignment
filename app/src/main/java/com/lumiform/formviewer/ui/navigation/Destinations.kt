package com.lumiform.formviewer.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object FormDestination

/** Only the id travels: the viewer reads the question from Room, so the route survives process death unchanged. */
@Serializable
data class ImageDestination(
    val questionId: Long,
)
