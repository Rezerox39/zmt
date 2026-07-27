package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.jyotiraditya.dmt.domain.model.ThemeOption

@Immutable
data class TuiColorPalette(
    val bg: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val bright: androidx.compose.ui.graphics.Color,
    val dim: androidx.compose.ui.graphics.Color,
    val faint: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val red: androidx.compose.ui.graphics.Color,
    val green: androidx.compose.ui.graphics.Color,
)

val AmoledPalette = TuiColorPalette(
    bg = TuiBg, surface = TuiSurface, fg = TuiFg, bright = TuiBright,
    dim = TuiDim, faint = TuiFaint, line = TuiLine, accent = TuiAccent,
    red = TuiRed, green = TuiGreen,
)

val RedAmoledPalette = TuiColorPalette(
    bg = RedBg, surface = RedSurface, fg = RedFg, bright = RedBright,
    dim = RedDim, faint = RedFaint, line = RedLine, accent = RedAccent,
    red = RedError, green = RedGreen,
)

val LiquidGlassPalette = TuiColorPalette(
    bg = GlassBg, surface = GlassSurface, fg = GlassFg, bright = GlassBright,
    dim = GlassDim, faint = GlassFaint, line = GlassLine, accent = GlassAccent,
    red = GlassError, green = GlassGreen,
)

val LocalTuiColors = staticCompositionLocalOf { AmoledPalette }

@Composable
fun TuiThemeProvider(
    theme: ThemeOption,
    content: @Composable () -> Unit,
) {
    val palette = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledPalette
        ThemeOption.RED_AMOLED -> RedAmoledPalette
        ThemeOption.LIQUID_GLASS -> LiquidGlassPalette
    }
    CompositionLocalProvider(LocalTuiColors provides palette) {
        content()
    }
}
