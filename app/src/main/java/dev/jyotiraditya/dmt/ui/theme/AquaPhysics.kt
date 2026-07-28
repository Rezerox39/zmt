package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Physics parameters that govern the liquid-glass rendering system.
 *
 * Every visual component reads these values so that the entire UI
 * behaves like a cohesive water-on-AMOLED surface.
 */
@Immutable
data class AquaPhysics(
    /** Surface tension — higher = more "bulge" / droplet feel (0..100) */
    val surfaceTension: Float = 18f,
    /** Viscosity — higher = slower, more sluggish animations (0..100) */
    val viscosity: Float = 35f,
    /** Reflection intensity on top edges (0..1) */
    val reflection: Float = 0.12f,
    /** Frosted blur radius in dp (0..40) */
    val blurRadius: Dp = 22.dp,
    /** Base transparency of glass surfaces (0..1) — 1 = opaque */
    val transparency: Float = 0.82f,
    /** Ripple intensity on touch (0..1) */
    val rippleIntensity: Float = 0.15f,
    /** Animation speed multiplier — 1.0 = normal */
    val animationSpeed: Float = 1.0f,
    /** Corner radius for small cards / tiles */
    val cornerSmall: Dp = 28.dp,
    /** Corner radius for large cards */
    val cornerLarge: Dp = 36.dp,
    /** Corner radius for album art frames */
    val cornerAlbum: Dp = 24.dp,
    /** Corner radius for buttons */
    val cornerButton: Dp = 32.dp,
    /** Corner radius for search bar capsule */
    val cornerSearch: Dp = 40.dp,
    /** Top-edge highlight height */
    val highlightHeight: Dp = 2.dp,
    /** Reflection animation duration ms */
    val reflectionDurationMs: Int = 350,
    /** Floating animation amplitude in px */
    val floatAmplitude: Float = 2f,
    /** Floating animation period ms */
    val floatPeriodMs: Int = 6000,
    /** Whether to render liquid glass effects (false = fallback to flat) */
    val enableGlass: Boolean = true,
    /** Liquid progress bar wave speed ms */
    val wavePeriodMs: Int = 2000,
) {
    /** Derived: Spring stiffness for press animations */
    val springStiffness: Float
        get() = Spring.StiffnessLow * (1f + (100f - viscosity) / 100f * 0.5f)

    /** Derived: Spring damping for press animations */
    val springDamping: Float
        get() = Spring.DampingRatioMediumBouncy * (1f + viscosity / 100f * 0.3f)
}

/** Default physics for the Aqua Glass theme */
val AquaGlassPhysics = AquaPhysics()

/** Fallback physics for the Classic DMT theme (no glass effects) */
val ClassicPhysics = AquaPhysics(
    surfaceTension = 0f,
    viscosity = 50f,
    reflection = 0f,
    blurRadius = 0.dp,
    transparency = 1f,
    rippleIntensity = 0f,
    enableGlass = false,
    cornerSmall = 8.dp,
    cornerLarge = 12.dp,
    cornerAlbum = 8.dp,
    cornerButton = 8.dp,
    cornerSearch = 8.dp,
)

/**
 * Runtime animation state for a liquid-glass surface.
 * Every interactive component that wants water-like feedback
 * creates one via [rememberAquaSurfaceState].
 */
@Stable
class AquaSurfaceState {
    /** Press scale — bulges slightly when pressed */
    var pressScale by mutableFloatStateOf(1f)
    /** Reflection X-offset — shifts on press */
    var reflectionOffset by mutableFloatStateOf(0f)
    /** Ripple phase for animated effects */
    var ripplePhase by mutableFloatStateOf(0f)
    /** Current surface tension bulge (0 = flat, 1 = max bulge) */
    var bulge by mutableFloatStateOf(0f)

    val animatablePressScale = Animatable(1f)
    val animatableReflection = Animatable(0f)
    val animatableRipple = Animatable(0f)
    val animatableBulge = Animatable(0f)
}

@Composable
fun rememberAquaSurfaceState(physics: AquaPhysics): AquaSurfaceState {
    return remember { AquaSurfaceState() }
}

/**
 * Glass highlight brush parameters computed from physics.
 */
data class GlassHighlight(
    val topColor: Color,
    val topHeight: Dp,
    val reflectionGradient: List<Pair<Float, Color>>,
    val shadowColor: Color,
    val shadowOffset: Dp,
    val shadowRadius: Dp,
)
