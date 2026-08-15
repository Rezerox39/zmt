package dev.abhi.zmt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.abhi.zmt.domain.model.ThemeOption

val LocalAccent = staticCompositionLocalOf { TuiAccent }

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
    theme: dev.abhi.zmt.domain.model.ThemeOption = dev.abhi.zmt.domain.model.ThemeOption.AMOLED_BLACK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledBlackScheme
        ThemeOption.RED_AMOLED -> RedAmoledScheme
        ThemeOption.LIQUID_GLASS -> LiquidGlassScheme
    }
    CompositionLocalProvider(LocalAccent provides TuiAccent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
