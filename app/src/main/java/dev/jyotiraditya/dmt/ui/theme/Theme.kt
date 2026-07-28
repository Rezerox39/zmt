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

private val AquaGlassScheme = darkColorScheme(
    primary = AquaBright,
    onPrimary = AquaBg,
    secondary = AquaFg,
    onSecondary = AquaBg,
    tertiary = AquaDim,
    onTertiary = AquaBg,
    background = AquaBg,
    onBackground = AquaFg,
    surface = AquaBg,
    onSurface = AquaFg,
    surfaceVariant = AquaSurface,
    onSurfaceVariant = AquaDim,
    outline = AquaCardBorder,
    error = AquaRed,
)

@Composable
fun DMTTheme(
    theme: ThemeOption = ThemeOption.AMOLED_BLACK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        ThemeOption.AMOLED_BLACK -> AmoledBlackScheme
        ThemeOption.AQUA_GLASS -> AquaGlassScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
