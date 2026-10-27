package com.anantenergies.bms.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens from CLAUDE.md, plus the few extra the mockup needs. */
@Immutable
data class AeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val border: Color,
    /** Dashed demo card, outlined buttons, switch track when off. */
    val borderStrong: Color,
    val navBar: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Brand, SOC ring, selected tab and segment. */
    val brand: Color,
    val onBrand: Color,
    /** Discharging, highest cell. */
    val discharge: Color,
    val dischargeBg: Color,
    val dischargeBorder: Color,
    /** Lowest cell, links. */
    val low: Color,
    /** OK, MOS on, charging. */
    val ok: Color,
    val okBg: Color,
    val okBorder: Color,
    val connectedDot: Color,
    val balancingBg: Color,
    val balancingText: Color,
    /** Active protections, cells outside the datasheet range. */
    val error: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val barHigh: Color,
    val barLow: Color,
    val barOther: Color,
)

val DarkColors = AeColors(
    isDark = true,
    background = Color(0xFF0E1315),
    surface = Color(0xFF172024),
    border = Color(0xFF26323A),
    borderStrong = Color(0xFF3A4A52),
    navBar = Color(0xFF121A1D),
    textPrimary = Color(0xFFE8EEF0),
    textSecondary = Color(0xFF9AA8AE),
    brand = Color(0xFF3CBED0),
    onBrand = Color(0xFF0E1315),
    discharge = Color(0xFFFFC27A),
    dischargeBg = Color(0xFF3A2A14),
    dischargeBorder = Color(0xFF5A3A22),
    low = Color(0xFF8EC5FF),
    ok = Color(0xFF6FE0C4),
    okBg = Color(0xFF173A33),
    okBorder = Color(0xFF2F5A50),
    connectedDot = Color(0xFF3CC8A8),
    balancingBg = Color(0xFF1E3550),
    balancingText = Color(0xFF9FD0FF),
    error = Color(0xFFFF8A80),
    errorBg = Color(0xFF3D1C1C),
    errorBorder = Color(0xFF6B2E2E),
    barHigh = Color(0xFFF59E3B),
    barLow = Color(0xFF5AA9F0),
    barOther = Color(0xFF4A5A62),
)

val LightColors = AeColors(
    isDark = false,
    background = Color(0xFFF4F6F7),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFDCE2E5),
    borderStrong = Color(0xFFC2CBD0),
    navBar = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF11181B),
    textSecondary = Color(0xFF5A676D),
    brand = Color(0xFF138A9C),
    // White on the light brand colour is only 4.1:1; this passes 4.5:1.
    onBrand = Color(0xFF0E1315),
    discharge = Color(0xFFA0560B),
    dischargeBg = Color(0xFFFCEBD5),
    dischargeBorder = Color(0xFFE9C79F),
    low = Color(0xFF1F64B5),
    ok = Color(0xFF0E7A62),
    okBg = Color(0xFFDDF3EC),
    okBorder = Color(0xFF9FD6C6),
    connectedDot = Color(0xFF12A37F),
    balancingBg = Color(0xFFDCE8F7),
    balancingText = Color(0xFF1F64B5),
    error = Color(0xFFB3261E),
    errorBg = Color(0xFFFCE4E2),
    errorBorder = Color(0xFFF1B5AF),
    barHigh = Color(0xFFC26A12),
    barLow = Color(0xFF2F7BD0),
    barOther = Color(0xFFAEB9BE),
)
