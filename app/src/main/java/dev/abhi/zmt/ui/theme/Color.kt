package dev.abhi.zmt.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.abhi.zmt.domain.model.AccentColor

// AMOLED Black (default)
val TuiBg = Color(0xFF000000)
val TuiSurface = Color(0xFF131415)
val TuiRaised = Color(0xFF080808)
val TuiFg = Color(0xFFC6CBCC)
val TuiBright = Color(0xFFDFE4E5)
val TuiDim = Color(0xFF767D80)
val TuiFaint = Color(0xFF3C4245)
val TuiLine = Color(0xFF1E2122)
var TuiAccent by mutableStateOf(Color(0xFFDC143C))
val TuiRed = Color(0xFFB85C50)
val TuiGreen = Color(0xFF7FA05F)

fun AccentColor.toColor(): Color = Color(argb)

// Red AMOLED
val RedBg = Color(0xFF000000)
val RedSurface = Color(0xFF1A0A0A)
val RedFg = Color(0xFFE0C0C0)
val RedBright = Color(0xFFF0D0D0)
val RedDim = Color(0xFF806060)
val RedFaint = Color(0xFF402020)
val RedLine = Color(0xFF2A1010)
val RedAccent = Color(0xFFE05050)
val RedError = Color(0xFFFF6060)
val RedGreen = Color(0xFF7FA05F)

// Liquid Glass
val GlassBg = Color(0xFF0A0A10)
val GlassSurface = Color(0xFF181828)
val GlassFg = Color(0xFFD0D8E8)
val GlassBright = Color(0xFFE8F0FF)
val GlassDim = Color(0xFF7880A0)
val GlassFaint = Color(0xFF303050)
val GlassLine = Color(0xFF202040)
val GlassAccent = Color(0xFF80C0FF)
val GlassError = Color(0xFFB85C50)
val GlassGreen = Color(0xFF7FA05F)
