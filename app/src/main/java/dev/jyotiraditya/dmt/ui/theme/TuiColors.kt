package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.jyotiraditya.dmt.domain.model.ThemeOption

/**
 * Complete visual palette for a UI theme.
 *
 * Every colour here is read from [LocalTuiColors] at runtime,
 * so a theme switch instantly updates every composable without
 * any hardcoded fallback.
 */
@Immutable
data class TuiColorPalette(
    val bg: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val card: androidx.compose.ui.graphics.Color,
    val cardBorder: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val bright: androidx.compose.ui.graphics.Color,
    val dim: androidx.compose.ui.graphics.Color,
    val faint: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val accentDim: androidx.compose.ui.graphics.Color,
    val highlight: androidx.compose.ui.graphics.Color,
    val glow: androidx.compose.ui.graphics.Color,
    val red: androidx.compose.ui.graphics.Color,
    val green: androidx.compose.ui.graphics.Color,
)

// ── Palettes ────────────────────────────────────────────────────

val AmoledPalette = TuiColorPalette(
    bg = TuiBg,
    surface = TuiSurface,
    card = TuiSurface,
    cardBorder = TuiLine,
    fg = TuiFg,
    bright = TuiBright,
    dim = TuiDim,
    faint = TuiFaint,
    line = TuiLine,
    accent = TuiAccent,
    accentDim = TuiAccent.copy(alpha = 0.6f),
    highlight = TuiBright.copy(alpha = 0.08f),
    glow = TuiAccent.copy(alpha = 0.04f),
    red = TuiRed,
    green = TuiGreen,
)

val AquaGlassPalette = TuiColorPalette(
    bg = AquaBg,
    surface = AquaSurface,
    card = AquaCardBase,
    cardBorder = AquaCardBorder,
    fg = AquaFg,
    bright = AquaBright,
    dim = AquaDim,
    faint = AquaFaint,
    line = AquaCardBorder,
    accent = AquaAccent,
    accentDim = AquaAccentDim,
    highlight = AquaHighlight,
    glow = AquaGlow,
    red = AquaRed,
    green = AquaGreen,
)

// ── Composition Local ───────────────────────────────────────────

val LocalTuiColors = staticCompositionLocalOf { AmoledPalette }
val LocalAquaPhysics = staticCompositionLocalOf { ClassicPhysics }

@Composable
fun TuiThemeProvider(
    theme: ThemeOption,
    content: @Composable () -> Unit,
) {
    val (palette, physics) = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledPalette to ClassicPhysics
        ThemeOption.AQUA_GLASS -> AquaGlassPalette to AquaGlassPhysics
    }
    CompositionLocalProvider(
        LocalTuiColors provides palette,
        LocalAquaPhysics provides physics,
    ) {
        content()
    }
}
