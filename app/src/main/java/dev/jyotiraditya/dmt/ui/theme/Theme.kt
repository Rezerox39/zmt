package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import dev.jyotiraditya.dmt.domain.model.ThemeOption

private val AmoledBlackScheme = darkColorScheme(
    primary = TuiBright,
    onPrimary = TuiBg,
    secondary = TuiFg,
    onSecondary = TuiBg,
    tertiary = TuiDim,
    onTertiary = TuiBg,
    background = TuiBg,
    onBackground = TuiFg,
    surface = TuiBg,
    onSurface = TuiFg,
    surfaceVariant = TuiSurface,
    onSurfaceVariant = TuiDim,
    outline = TuiLine,
    error = TuiRed,
)

private val RedAmoledScheme = darkColorScheme(
    primary = RedBright,
    onPrimary = RedBg,
    secondary = RedFg,
    onSecondary = RedBg,
    tertiary = RedDim,
    onTertiary = RedBg,
    background = RedBg,
    onBackground = RedFg,
    surface = RedBg,
    onSurface = RedFg,
    surfaceVariant = RedSurface,
    onSurfaceVariant = RedDim,
    outline = RedLine,
    error = RedError,
)

private val LiquidGlassScheme = darkColorScheme(
    primary = GlassBright,
    onPrimary = GlassBg,
    secondary = GlassFg,
    onSecondary = GlassBg,
    tertiary = GlassDim,
    onTertiary = GlassBg,
    background = GlassBg,
    onBackground = GlassFg,
    surface = GlassBg,
    onSurface = GlassFg,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = GlassDim,
    outline = GlassLine,
    error = GlassError,
)

@Composable
fun DMTTheme(
    theme: dev.jyotiraditya.dmt.domain.model.ThemeOption = dev.jyotiraditya.dmt.domain.model.ThemeOption.AMOLED_BLACK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledBlackScheme
        ThemeOption.RED_AMOLED -> RedAmoledScheme
        ThemeOption.LIQUID_GLASS -> LiquidGlassScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
