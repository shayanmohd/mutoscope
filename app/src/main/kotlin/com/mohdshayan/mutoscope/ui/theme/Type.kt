@file:OptIn(ExperimentalTextApi::class)

package com.mohdshayan.mutoscope.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.mutoscope.R

/*
 * Anybody is the one loud face: a width-variable grotesque, squash and stretch in type. It sets
 * the Projects title and empty-state headlines at weight 750, width 125, and sheet titles at 650,
 * width 100. Public Sans carries everything else at 400, 500 and 600. Both are bundled variable
 * fonts (SIL OFL 1.1, licences in docs/). Counters use tabular figures so digits hold still.
 */
private val AnybodyWide = FontFamily(
    Font(
        R.font.anybody_variable,
        weight = FontWeight(750),
        variationSettings = FontVariation.Settings(FontVariation.weight(750), FontVariation.width(125f)),
    ),
)

private val AnybodyTitle = FontFamily(
    Font(
        R.font.anybody_variable,
        weight = FontWeight(650),
        variationSettings = FontVariation.Settings(FontVariation.weight(650), FontVariation.width(100f)),
    ),
)

private val PublicSans = FontFamily(
    Font(R.font.publicsans_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.publicsans_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.publicsans_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

const val TabularFigures = "tnum"

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AnybodyWide,
        fontWeight = FontWeight(750),
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AnybodyWide,
        fontWeight = FontWeight(750),
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AnybodyTitle,
        fontWeight = FontWeight(650),
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TabularFigures,
    ),
    labelSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TabularFigures,
    ),
)
