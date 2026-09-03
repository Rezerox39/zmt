package dev.abhi.zmt.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * ZMT Motion Tokens — consistent animation language.
 *
 * Micro:    ~120–180ms — button press, icon state, toggles
 * Standard: ~220–320ms — content changes, visibility, navigation
 * Hero:     ~400–550ms — mini→full player, artwork transformations
 */
object ZmtMotion {
    // ── Micro Interactions ──
    val microFast = tween<Float>(durationMillis = 120)
    val micro = tween<Float>(durationMillis = 160)
    val microSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    // ── Standard Transitions ──
    val standard = tween<Float>(durationMillis = 260)
    val standardSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val standardSoft = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // ── Hero Transitions ──
    val hero = tween<Float>(durationMillis = 450)
    val heroSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
    val heroSmooth = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    // ── Player Sheet ──
    val sheetSnap = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )
}
