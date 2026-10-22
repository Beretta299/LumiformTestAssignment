package com.lumiform.formviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own palette: ink on paper with one signal colour, rather than Material's colour roles. The form reads
 * these tokens directly; [toMaterial] maps them onto a Material scheme only for the stock components it still uses
 * (snackbar, buttons, loading indicator, pull-to-refresh).
 */
@Immutable
data class FormColors(
    val paper: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val track: Color,
    val hero: Color,
    val onHero: Color,
    val heroMuted: Color,
    val heroTrack: Color,
    val selection: Color,
    val onSelection: Color,
    val signal: Color,
    /** [signal] for text on paper: the light theme's orange is too pale for small numbers (WCAG 4.5:1). */
    val signalText: Color,
    val onSignal: Color,
    val signalSoft: Color,
    val success: Color,
    val warning: Color,
)

internal val LightFormColors =
    FormColors(
        paper = Color(0xFFF4F2EE),
        surface = Color(0xFFFBFAF7),
        text = Color(0xFF16161A),
        muted = Color(0xFF6B675F),
        outline = Color(0xFFD9D5CC),
        track = Color(0xFFE9E6DF),
        hero = Color(0xFF16161A),
        onHero = Color(0xFFF4F2EE),
        heroMuted = Color(0xFFA8A49B),
        heroTrack = Color(0xFF3A3A40),
        selection = Color(0xFF16161A),
        onSelection = Color(0xFFF4F2EE),
        signal = Color(0xFFFF5A1F),
        signalText = Color(0xFFC2410C),
        onSignal = Color(0xFF16161A),
        signalSoft = Color(0xFFFFF1EA),
        success = Color(0xFF1E9E6A),
        warning = Color(0xFFFFB84D),
    )

internal val DarkFormColors =
    FormColors(
        paper = Color(0xFF111113),
        surface = Color(0xFF1A1A1E),
        text = Color(0xFFF1EFEA),
        muted = Color(0xFFA19D94),
        outline = Color(0xFF34343A),
        track = Color(0xFF232328),
        hero = Color(0xFF1F1F24),
        onHero = Color(0xFFF4F2EE),
        heroMuted = Color(0xFFA8A49B),
        heroTrack = Color(0xFF3A3A40),
        selection = Color(0xFFF1EFEA),
        onSelection = Color(0xFF16161A),
        signal = Color(0xFFFF6A33),
        signalText = Color(0xFFFF6A33),
        onSignal = Color(0xFF16161A),
        signalSoft = Color(0xFF3A2016),
        success = Color(0xFF2BB57C),
        warning = Color(0xFFFFB84D),
    )

val LocalFormColors = staticCompositionLocalOf { LightFormColors }

internal fun FormColors.toMaterial(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = signal,
        onPrimary = onSignal,
        primaryContainer = signalSoft,
        onPrimaryContainer = text,
        secondary = selection,
        onSecondary = onSelection,
        background = paper,
        onBackground = text,
        surface = paper,
        onSurface = text,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = track,
        surfaceContainerHigh = track,
        surfaceContainerHighest = track,
        outline = outline,
        outlineVariant = outline,
        inverseSurface = hero,
        inverseOnSurface = onHero,
    )
}
