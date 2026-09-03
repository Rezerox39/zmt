package dev.abhi.zmt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.abhi.zmt.R

val PlexMono = FontFamily(
    Font(R.font.plex_mono, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
    Font(R.font.plex_mono_italic, FontWeight.Normal, FontStyle.Italic),
)

private fun mono(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
    lineHeight: Int = size + 8,
) = TextStyle(
    fontFamily = PlexMono,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeight = lineHeight.sp,
)

/**
 * ZMT Typography — refined hierarchy.
 *
 * Display:   32sp bold  — hero numbers, large statistics
 * Headline:  21sp bold  — screen titles, now-playing track
 * Title:     15sp bold  — section headings, album names
 * Body:      14sp normal — primary readable text, track titles
 * Body sm:   13sp normal — secondary text, descriptions
 * Label L:   13sp bold tracking 1 — section tags, category labels
 * Label M:   12sp tracking 1.5 — metadata, timestamps, technical info
 * Label S:   11sp medium tracking 0.5 — tiny badges, status indicators
 * Caption:   10sp normal tracking 0.5 — micro text, dim metadata
 */
val Typography = Typography(
    displayLarge = mono(32, FontWeight.Bold),
    headlineLarge = mono(21, FontWeight.Bold),
    headlineSmall = mono(15, FontWeight.Normal),
    titleLarge = mono(21, FontWeight.Bold),
    titleMedium = mono(15, FontWeight.Bold),
    titleSmall = mono(13, FontWeight.Bold, tracking = 0.5f),
    bodyLarge = mono(14),
    bodyMedium = mono(13),
    bodySmall = mono(11, tracking = 0.5f),
    labelLarge = mono(13, FontWeight.Bold, tracking = 1f),
    labelMedium = mono(12, tracking = 1.5f),
    labelSmall = mono(11, FontWeight.Medium, tracking = 0.5f),
)
