package com.lumiform.formviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The app's own look (see [FormColors]) on Material 3 Expressive, whose spring motion scheme drives the animations. */
@Composable
fun FormViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFormColors else LightFormColors
    CompositionLocalProvider(LocalFormColors provides colors) {
        MaterialExpressiveTheme(
            colorScheme = colors.toMaterial(darkTheme),
            motionScheme = MotionScheme.expressive(),
            typography = FormTypography,
            content = content,
        )
    }
}

object FormTheme {
    val colors: FormColors
        @Composable @ReadOnlyComposable
        get() = LocalFormColors.current
}
