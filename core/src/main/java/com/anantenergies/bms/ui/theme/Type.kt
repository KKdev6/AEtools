package com.anantenergies.bms.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.anantenergies.bms.core.R

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** For every number on screen. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Bold labels of the readings in cards (owner decision). */
val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_bold, FontWeight.Bold),
)

/** Text styles. Colours are left unset; text takes the current content colour unless given one. */
@Immutable
data class AeType(
    val screenTitle: TextStyle,
    val headerTitle: TextStyle,
    val cardTitle: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val sectionLabel: TextStyle,
    val tag: TextStyle,
    /** Labels of readings, e.g. "Voltage". */
    val parameter: TextStyle,
    /** Smaller labels of readings, e.g. "Highest · #7", "T1". */
    val parameterSmall: TextStyle,
    val numberHero: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
)

private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Double = 1.35) =
    TextStyle(fontFamily = PlexSans, fontWeight = weight, fontSize = size.sp, lineHeight = (size * lineHeight).sp)

private fun mono(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = PlexMono, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.25).sp)

// Slightly tightened, as in the reference; also keeps "Highest · #12" on one line on 360 dp phones.
private fun parameterLabel(size: Int) = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * 1.35).sp,
    letterSpacing = (-0.02).em,
)

val DefaultType = AeType(
    screenTitle = sans(26, FontWeight.SemiBold, lineHeight = 1.2),
    headerTitle = sans(18, FontWeight.SemiBold, lineHeight = 1.25),
    cardTitle = sans(14, FontWeight.SemiBold),
    bodyLarge = sans(15),
    body = sans(14, lineHeight = 1.45),
    small = sans(13),
    label = sans(12),
    sectionLabel = sans(12, FontWeight.SemiBold).copy(letterSpacing = 0.06.em),
    tag = sans(11, FontWeight.Medium),
    parameter = parameterLabel(13),
    parameterSmall = parameterLabel(12),
    numberHero = mono(36, FontWeight.SemiBold).copy(lineHeight = 36.sp),
    numberLarge = mono(18, FontWeight.Medium),
    numberMedium = mono(16),
    number = mono(14),
    numberSmall = mono(12),
)

/** Material's own styles, switched to Plex Sans, for the few Material components we use. */
val MaterialType: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = PlexSans),
        displayMedium = displayMedium.copy(fontFamily = PlexSans),
        displaySmall = displaySmall.copy(fontFamily = PlexSans),
        headlineLarge = headlineLarge.copy(fontFamily = PlexSans),
        headlineMedium = headlineMedium.copy(fontFamily = PlexSans),
        headlineSmall = headlineSmall.copy(fontFamily = PlexSans),
        titleLarge = titleLarge.copy(fontFamily = PlexSans),
        titleMedium = titleMedium.copy(fontFamily = PlexSans),
        titleSmall = titleSmall.copy(fontFamily = PlexSans),
        bodyLarge = bodyLarge.copy(fontFamily = PlexSans),
        bodyMedium = bodyMedium.copy(fontFamily = PlexSans),
        bodySmall = bodySmall.copy(fontFamily = PlexSans),
        labelLarge = labelLarge.copy(fontFamily = PlexSans),
        labelMedium = labelMedium.copy(fontFamily = PlexSans),
        labelSmall = labelSmall.copy(fontFamily = PlexSans),
    )
}
