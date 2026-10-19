package com.anantenergies.bms.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.anantenergies.bms.R
import com.anantenergies.bms.ui.theme.AeTheme

/** The AE mark: cyan on dark, the deeper cyan file on light. */
@Composable
fun AeMark(height: Dp, modifier: Modifier = Modifier, contentDescription: String? = "Anant Energies") {
    val painter = painterResource(if (AeTheme.colors.isDark) R.drawable.ae_mark else R.drawable.ae_mark_dark)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.height(height).aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
    )
}

/** The ANANT ENERGIES wordmark: cyan on dark, black on light (owner decision; the cyan is unreadable on white). */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) {
    val painter = painterResource(R.drawable.anant_wordmark)
    Image(
        painter = painter,
        contentDescription = "Anant Energies",
        modifier = modifier.height(height).aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
        colorFilter = if (AeTheme.colors.isDark) null else ColorFilter.tint(Color.Black),
    )
}
