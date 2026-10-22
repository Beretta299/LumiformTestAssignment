package com.lumiform.formviewer.ui.form

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/** Size-dependent dimensions of the form: compact on phones, roomier from the medium width class (foldables, tablets). */
@Immutable
data class FormLayout(
    val horizontalPadding: Dp,
    /** Deeper sections stop indenting, so text keeps a readable width. */
    val maxIndentLevels: Int,
    val thumbnailHeight: Dp,
) {
    companion object {
        val Compact = FormLayout(horizontalPadding = 16.dp, maxIndentLevels = 3, thumbnailHeight = 168.dp)
        val Wide = FormLayout(horizontalPadding = 24.dp, maxIndentLevels = 6, thumbnailHeight = 240.dp)

        /** Wider screens center the form instead of stretching lines past a comfortable reading length. */
        val MaxContentWidth = 720.dp
    }
}

@Composable
fun currentFormLayout(): FormLayout {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        FormLayout.Wide
    } else {
        FormLayout.Compact
    }
}
