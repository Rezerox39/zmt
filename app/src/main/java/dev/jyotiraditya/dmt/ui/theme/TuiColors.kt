package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.jyotiraditya.dmt.domain.model.ThemeOption

/**
 * Complete color palette for a UI theme.
 * Every visual attribute maps to a consistent design language.
 *
 * Extends the basic palette with card-specific colors for
 * glass/droplet/matte surface rendering across all components.
 */
@Immutable
data class TuiColorPalette(
    val bg: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val card: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val bright: androidx.compose.ui.graphics.Color,
    val dim: androidx.compose.ui.graphics.Color,
    val faint: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val accentDim: androidx.compose.ui.graphics.Color,
    val red: androidx.compose.ui.graphics.Color,
    val green: androidx.compose.ui.graphics.Color,
)

// ── Palettes ────────────────────────────────────────────────────

val AmoledPalette = TuiColorPalette(
    bg = TuiBg, surface = TuiSurface, card = TuiSurface,
    fg = TuiFg, bright = TuiBright,
    dim = TuiDim, faint = TuiFaint, line = TuiLine,
    accent = TuiAccent, accentDim = TuiAccent.copy(alpha = 0.6f),
    red = TuiRed, green = TuiGreen,
)

val AquaGlassPalette = TuiColorPalette(
    bg = AquaBg, surface = AquaSurface, card = AquaCard,
    fg = AquaFg, bright = AquaBright,
    dim = AquaDim, faint = AquaFaint, line = AquaLine,
    accent = AquaAccent, accentDim = AquaAccentDim,
    red = AquaRed, green = AquaGreen,
)

val CrimsonNoirPalette = TuiColorPalette(
    bg = NoirBg, surface = NoirSurface, card = NoirCard,
    fg = NoirFg, bright = NoirBright,
    dim = NoirDim, faint = NoirFaint, line = NoirLine,
    accent = NoirAccent, accentDim = NoirAccentDim,
    red = NoirRed, green = NoirGreen,
)

// ── Composition Local ───────────────────────────────────────────

val LocalTuiColors = staticCompositionLocalOf { AmoledPalette }

@Composable
fun TuiThemeProvider(
    theme: ThemeOption,
    content: @Composable () -> Unit,
) {
    val palette = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledPalette
        ThemeOption.AQUA_GLASS -> AquaGlassPalette
        ThemeOption.CRIMSON_NOIR -> CrimsonNoirPalette
    }
    CompositionLocalProvider(LocalTuiColors provides palette) {
        content()
    }
}
