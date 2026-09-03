package dev.abhi.zmt.core.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Extracts a palette from album artwork for ambient tinting.
 * Returns dominant + vibrant + muted colors from the bitmap.
 * No external dependencies — pure pixel sampling.
 */
@Immutable
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
)

private fun Color.isNeutral() = run {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val saturation = if (max == 0f) 0f else (max - min) / max
    val brightness = max
    saturation < 0.15f || brightness < 0.1f || brightness > 0.95f
}

private fun Color.isCloseTo(other: Color) =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.15f

/**
 * Sample a bitmap at a grid of points and cluster into dominant/vibrant/muted.
 * Fast enough for album art (256×256 or similar).
 */
fun extractArtworkPalette(bitmap: Bitmap): ArtworkPalette {
    val sampleStep = maxOf(1, bitmap.width / 32)
    var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
    var bestVibrant = Color(0.5f, 0.5f, 0.5f)
    var bestVibrantSat = 0f
    var bestMuted = Color(0.5f, 0.5f, 0.5f)
    var bestMutedSat = 1f

    for (y in 0 until bitmap.height step sampleStep) {
        for (x in 0 until bitmap.width step sampleStep) {
            val pixel = bitmap.getPixel(x, y)
            val c = Color(android.graphics.Color.red(pixel) / 255f,
                          android.graphics.Color.green(pixel) / 255f,
                          android.graphics.Color.blue(pixel) / 255f)
            rSum += (c.red * 255).toLong()
            gSum += (c.green * 255).toLong()
            bSum += (c.blue * 255).toLong()
            count++

            val max = maxOf(c.red, c.green, c.blue)
            val min = minOf(c.red, c.green, c.blue)
            val sat = if (max == 0f) 0f else (max - min) / max
            val brightness = max

            if (sat > bestVibrantSat && brightness > 0.25f) {
                bestVibrantSat = sat
                bestVibrant = c
            }
            if (sat < bestMutedSat && brightness in 0.2f..0.7f) {
                bestMutedSat = sat
                bestMuted = c
            }
        }
    }

    if (count == 0) return ArtworkPalette(Color(0.3f, 0.3f, 0.3f), Color(0.5f, 0.5f, 0.5f), Color(0.4f, 0.4f, 0.4f))

    val dominant = Color(
        red = (rSum.toFloat() / count / 255f).coerceIn(0f, 1f),
        green = (gSum.toFloat() / count / 255f).coerceIn(0f, 1f),
        blue = (bSum.toFloat() / count / 255f).coerceIn(0f, 1f),
    )

    return ArtworkPalette(
        dominant = if (dominant.isNeutral()) dominant.copy(red = 0.35f, green = 0.35f, blue = 0.35f) else dominant,
        vibrant = if (bestVibrant.isNeutral()) dominant else bestVibrant,
        muted = bestMuted,
    )
}

@Composable
fun rememberArtworkPalette(bitmap: Bitmap?): ArtworkPalette? {
    if (bitmap == null) return null
    return remember(bitmap) {
        try { extractArtworkPalette(bitmap) } catch (_: Exception) { null }
    }
}
