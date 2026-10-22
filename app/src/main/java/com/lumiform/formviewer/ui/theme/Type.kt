package com.lumiform.formviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumiform.formviewer.R

// Both are bundled variable fonts (SIL OFL, licences in assets/licenses), so the type is the same offline and on
// every device.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    resId: Int,
    vararg weights: FontWeight,
) = FontFamily(
    weights.map {
        Font(resId, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

/** Space Grotesk for headings and numbers. */
private val DisplayFamily =
    variableFamily(R.font.space_grotesk, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Inter for reading text and controls. */
private val BodyFamily = variableFamily(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

private fun display(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Bold,
) = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = (-0.02).em,
)

private fun body(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontFamily = BodyFamily, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/** Only the styles the app uses; the rest keep Material's defaults. */
internal val FormTypography =
    Typography(
        displaySmall = display(36, 40),
        headlineLarge = display(32, 38),
        headlineSmall = display(24, 30),
        titleMedium = display(19, 24),
        titleSmall = display(17, 22, FontWeight.SemiBold),
        bodyLarge = body(16, 23),
        bodyMedium = body(15, 22),
        labelLarge = body(14, 20, FontWeight.SemiBold),
        labelMedium = body(12, 16, FontWeight.Medium),
        labelSmall = display(13, 16, FontWeight.Bold),
    )
