package com.lumiform.formviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle

/** The one place that maps the form hierarchy to text styles: page largest, sections shrink with depth, questions smallest. */
object HierarchyTypography {
    val page: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineLarge

    val question: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    /** Item numbers like "2.1" sit beside the text they number. */
    val number: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall

    /** Floors at `titleSmall` (17sp), so a deep section never gets smaller than question text (16sp). */
    @Composable
    @ReadOnlyComposable
    fun section(depth: Int): TextStyle =
        when (depth) {
            0 -> MaterialTheme.typography.headlineSmall
            1 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleSmall
        }
}
