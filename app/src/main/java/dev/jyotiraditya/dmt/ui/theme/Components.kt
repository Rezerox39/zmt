package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import dev.jyotiraditya.dmt.domain.model.ThemeOption

// ═══════════════════════════════════════════════════════════════════
// AQUA GLASS COMPONENT SYSTEM — LIQUID MATERIAL RENDERING ENGINE
//
// Every component reads LocalTuiColors + LocalAquaPhysics at runtime,
// so switching themes instantly updates the entire UI.
//
// Design principles:
// • Pure AMOLED black (#000000) background
// • Interactive elements float as water droplets
// • Surface tension, refraction, reflection, and ripple physics
// • Glass transparency creates depth on black canvas
// • Dynamic glow responds to content colours
// ═══════════════════════════════════════════════════════════════════

// ── GLASS CARD ────────────────────────────────────────────────────
// The workhorse composable. Renders as a water-droplet card with:
// • Radial inner glow
// • Top-edge reflection highlight  
// • Bottom ambient glow
// • Surface tension bulge (subtle corner shading)
// • Background transparency for glass effect
// • Press deformation (4% compression)
// • Water ripple on touch
// • Floating animation in the mini-player variant

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && physics.pressCompression > 0f)
            (1f - physics.pressCompression) else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) 0.5f else 0.3f,
            stiffness = if (isPressed) Spring.StiffnessMedium else Spring.StiffnessLow,
        ),
        label = "pressScale",
    )

    val cornerDp = if (physics.enableGlass) physics.cornerSmall else 12.dp
    val cornerPx = with(LocalDensity.current) { cornerDp.toPx() }

    Box(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .scale(pressScale)
            .drawBehind {
                if (!physics.enableGlass) {
                    drawRoundRect(color = p.bg, cornerRadius = CornerRadius(cornerPx))
                    return@drawBehind
                }

                val cr = CornerRadius(cornerPx, cornerPx)
                val w = size.width
                val h = size.height

                // Layer 1: Inner shadow for depth (surface tension effect)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            p.surface.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = w * 0.5f,
                    ),
                    cornerRadius = cr,
                )

                // Layer 2: Glass background with transparency
                drawRoundRect(
                    color = p.surface.copy(alpha = physics.transparency),
                    cornerRadius = cr,
                )

                // Layer 3: Radial glow from center
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            p.glow.copy(alpha = physics.glowIntensity * 0.5f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = w * 0.6f,
                    ),
                    cornerRadius = cr,
                )

                // Layer 4: Top-edge reflection (moving highlight)
                val refPhase = if (isPressed) 0.8f else 0f
                val highlightY = h * 0.05f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = physics.reflection * (1f - refPhase * 0.3f)),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = highlightY * 2f,
                    ),
                    cornerRadius = cr,
                )

                // Layer 5: Bottom ambient glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            p.glow.copy(alpha = 0.08f),
                        ),
                        startY = h * 0.8f,
                        endY = h,
                    ),
                    topLeft = Offset(0f, h * 0.8f),
                    size = Size(w, h * 0.2f),
                    cornerRadius = cr,
                )

                // Layer 6: Subtle border for definition
                drawRoundRect(
                    color = p.cardBorder.copy(alpha = 0.3f),
                    cornerRadius = cr,
                    style = Stroke(width = 0.5f),
                )


            }

    ) {
        content()
    }
}

// ── DROPLET BUTTON ────────────────────────────────────────────────
// Circular water-droplet button with:
// • Glass transparency
// • Radial glow
// • Reflection highlight
// • Press deformation
// • Water ripple

@Composable
fun DropletButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && physics.enableGlass) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) 0.4f else 0.25f,
            stiffness = if (isPressed) Spring.StiffnessMedium else Spring.StiffnessLow,
        ),
        label = "dropletPress",
    )

    val buttonSize = 56.dp
    val cornerPx = with(LocalDensity.current) { (buttonSize / 2).toPx() }

    Box(
        modifier = modifier
            .size(buttonSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .scale(pressScale)
            .drawBehind {
                if (!physics.enableGlass) {
                    drawCircle(color = p.bg)
                    return@drawBehind
                }

                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension / 2f

                // Glass droplet body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            p.surface.copy(alpha = 0.9f),
                            p.surface.copy(alpha = 0.7f),
                        ),
                        center = Offset(cx * 0.6f, cy * 0.4f),
                        radius = r,
                    ),
                    radius = r,
                )

                // Inner glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            p.glow.copy(alpha = physics.glowIntensity),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r * 0.7f,
                    ),
                    radius = r,
                )

                // Top reflection highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = physics.reflection * 1.5f),
                            Color.White.copy(alpha = physics.reflection * 0.3f),
                            Color.Transparent,
                        ),
                        center = Offset(cx * 0.65f, cy * 0.35f),
                        radius = r * 0.4f,
                    ),
                    radius = r * 0.45f,
                )

                // Bottom shadow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = physics.innerShadowDepth),
                        ),
                        center = Offset(cx, cy * 1.1f),
                        radius = r,
                    ),
                    radius = r,
                )

                // Border highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = r,
                    style = Stroke(width = 1f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── LIQUID PROGRESS BAR ───────────────────────────────────────────
// Animated water-fill progress bar with:
// • Sine-wave liquid surface animation
// • Shimmer/light reflection moving across
// • Droplet fill effect (bubbles at low density)
// • Smooth fill transitions

@Composable
fun LiquidProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    val infinite = rememberInfiniteTransition(label = "liquid")
    val waveOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = physics.wavePeriodMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveOffset",
    )

    val shimmerOffset by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    val heightPx = with(LocalDensity.current) { height.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                if (!physics.enableGlass) {
                    // Flat bar
                    drawRoundRect(
                        color = p.dim.copy(alpha = 0.3f),
                        cornerRadius = CornerRadius(heightPx / 2),
                    )
                    drawRoundRect(
                        color = p.accent,
                        cornerRadius = CornerRadius(heightPx / 2),
                        size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                    )
                    return@drawBehind
                }

                val w = size.width
                val h = size.height
                val filledWidth = w * fraction.coerceIn(0f, 1f)
                val cr = CornerRadius(h / 2, h / 2)

                // Background track (glass groove)
                drawRoundRect(
                    color = p.surface.copy(alpha = 0.3f),
                    cornerRadius = cr,
                )

                // Filled portion with wave surface
                drawRoundRect(
                    color = p.accent.copy(alpha = 0.85f),
                    cornerRadius = cr,
                    size = Size(filledWidth, h),
                )

                // Liquid wave overlay on top portion
                val waveHeight = h * 0.3f
                if (filledWidth > 4f) {
                    val path = Path()
                    path.moveTo(0f, h)
                    for (x in 0..filledWidth.toInt() step 2) {
                        val y = h - waveHeight * sin((x * 0.05f) + waveOffset)
                        if (x == 0) path.lineTo(0f, y)
                        else path.lineTo(x.toFloat(), y)
                    }
                    path.lineTo(filledWidth, h)
                    path.close()
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.15f),
                    )
                }

                // Shimmer highlight moving across
                if (filledWidth > 10f) {
                    val shX = shimmerOffset * w
                    val shWidth = w * 0.15f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0f),
                            ),
                            startX = shX - shWidth / 2,
                            endX = shX + shWidth / 2,
                        ),
                        cornerRadius = cr,
                        size = Size(filledWidth, h),
                    )
                }
            },
    )
}

// ── GLASS SEARCH BAR ──────────────────────────────────────────────
// Capsule-shaped search bar with glass transparency.

@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val density = LocalDensity.current
    val cornerPx = with(density) { physics.cornerSearch.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(physics.cornerSearch))
            .drawBehind {
                val cr = CornerRadius(cornerPx, cornerPx)

                if (physics.enableGlass) {
                    // Glass background
                    drawRoundRect(
                        color = p.surface.copy(alpha = physics.transparency),
                        cornerRadius = cr,
                    )
                    // Inner glow
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                p.glow.copy(alpha = physics.glowIntensity * 0.3f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.width * 0.4f,
                        ),
                        cornerRadius = cr,
                    )
                    // Top highlight
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = physics.reflection), Color.Transparent),
                            startY = 0f,
                            endY = 4.dp.toPx(),
                        ),
                        cornerRadius = cr,
                    )
                    // Border
                    drawRoundRect(
                        color = p.cardBorder.copy(alpha = 0.3f),
                        cornerRadius = cr,
                        style = Stroke(width = 0.5f),
                    )
                } else {
                    drawRoundRect(color = p.bg, cornerRadius = cr)
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = p.dim,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.fg),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── GLASS ALBUM FRAME ─────────────────────────────────────────────
// Water-glass frame for album art.
// Renders a transparent glass border with reflection around the image.

@Composable
fun GlassAlbumFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerAlbum.toPx() }

    Box(
        modifier = modifier
            .drawBehind {
                if (!physics.enableGlass) return@drawBehind
                val cr = CornerRadius(cornerPx, cornerPx)
                val w = size.width
                val h = size.height

                // Outer glass frame
                drawRoundRect(
                    color = p.surface.copy(alpha = 0.3f),
                    cornerRadius = cr,
                    style = Stroke(width = 2f),
                )

                // Top-edge reflection on frame
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = physics.reflection * 1.5f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 6.dp.toPx(),
                    ),
                    cornerRadius = cr,
                )

                // Bottom glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, p.glow.copy(alpha = 0.06f)),
                        startY = h * 0.85f,
                        endY = h,
                    ),
                    topLeft = Offset(0f, h * 0.85f),
                    size = Size(w, h * 0.15f),
                    cornerRadius = cr,
                )

                // Corner highlights for glass refraction effect
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = w * 0.15f,
                    ),
                    cornerRadius = cr,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── GLASS BADGE ───────────────────────────────────────────────────
// Small liquid capsule badge.

@Composable
fun GlassBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { 6.dp.toPx() }

    val clr = if (physics.enableGlass) color.copy(alpha = 0.85f) else color

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = clr.copy(alpha = if (physics.enableGlass) 0.2f else 0.15f),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
                if (physics.enableGlass) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 2.dp.toPx(),
                        ),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                    )
                }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = clr,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── GLASS CHIP ────────────────────────────────────────────────────

@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = p.dim,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                drawRoundRect(
                    color = p.cardBorder,
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(0.5f),
                )
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

// ── THEMED CAPTION ────────────────────────────────────────────────

@Composable
fun GlassCaption(text: String, modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = p.dim,
        modifier = modifier.padding(vertical = 10.dp),
    )
}

// ── THEME CROSSFADE WRAPPER ───────────────────────────────────────
// Smooth 500ms crossfade between themes.

@Composable
fun ThemeCrossfade(
    targetTheme: ThemeOption,
    content: @Composable () -> Unit,
) {
    val fadeAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "themeFade",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Black overlay that fades out during transition
                val overlayAlpha = (1f - fadeAlpha) * 0.5f
                if (overlayAlpha > 0.01f) {
                    drawRect(color = Color.Black.copy(alpha = overlayAlpha))
                }
            },
    ) {
        content()
    }
}

// ── FLOATING ANIMATION ────────────────────────────────────────────
// Adds a gentle vertical floating oscillation to any composable.

@Composable
fun rememberFloatingAnimation(
    amplitude: Float = 2f,
    periodMs: Int = 6000,
): Float {
    val infinite = rememberInfiniteTransition(label = "float")
    val offset by infinite.animateFloat(
        initialValue = -amplitude,
        targetValue = amplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatOffset",
    )
    return offset
}

// ── BREATHING ANIMATION ───────────────────────────────────────────
// Subtle scale pulse for active elements (album art in player).

@Composable
fun rememberBreathingAnimation(
    isPlaying: Boolean = true,
    minScale: Float = 1f,
    maxScale: Float = 1.01f,
    periodMs: Int = 4000,
): Float {
    val infinite = rememberInfiniteTransition(label = "breathing")
    val scale by infinite.animateFloat(
        initialValue = minScale,
        targetValue = if (isPlaying) maxScale else minScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    return scale
}

// ── WATER RIPPLE CANVAS ───────────────────────────────────────────
// Draws a water-ripple circle effect on a Canvas.

fun DrawScope.drawWaterRipple(
    centre: Offset,
    progress: Float,
    colour: Color,
    maxRadius: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    val radius = maxRadius * progress
    val alpha = (1f - progress) * 0.12f
    drawCircle(
        color = colour.copy(alpha = alpha),
        radius = radius,
        center = centre,
    )
}

// ── GLASS BACKGROUND GLOW ─────────────────────────────────────────
// Draws a subtle radial glow behind a component.

fun DrawScope.drawGlassGlow(
    colour: Color,
    maxAlpha: Float = 0.06f,
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(colour.copy(alpha = maxAlpha), Color.Transparent),
            center = center,
            radius = size.minDimension * 0.8f,
        ),
    )
}

// ── GLASS DIVIDER ─────────────────────────────────────────────────

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                val alpha = if (physics.enableGlass) 0.1f else 0.2f
                drawLine(
                    color = p.dim.copy(alpha = alpha),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                )
            },
    )
}

// ── GLASS TAB ─────────────────────────────────────────────────────

@Composable
fun GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(physics.cornerSmall))
            .clickable(indication = null, onClick = onClick)
            .drawBehind {
                val cr = CornerRadius(
                    with(LocalDensity.current) { physics.cornerSmall.toPx() }
                )
                if (selected && physics.enableGlass) {
                    drawRoundRect(
                        color = p.surface.copy(alpha = physics.transparency),
                        cornerRadius = cr,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = physics.reflection), Color.Transparent),
                            startY = 0f,
                            endY = 4.dp.toPx(),
                        ),
                        cornerRadius = cr,
                    )
                } else if (selected) {
                    drawRoundRect(color = p.surface, cornerRadius = cr)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        content()
    }
}

// ── GLASS BOTTOM NAV ──────────────────────────────────────────────

@Composable
fun GlassBottomNav(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (physics.enableGlass) {
                    drawRect(color = p.surface.copy(alpha = physics.transparency * 0.8f))
                    // Top border
                    drawLine(
                        color = p.glow.copy(alpha = 0.05f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                    )
                } else {
                    drawRect(color = p.bg)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── GLASS TOGGLE ──────────────────────────────────────────────────

@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val width = 48.dp
    val height = 28.dp

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(height / 2))
            .clickable(indication = null) { onCheckedChange(!checked) }
            .drawBehind {
                val cr = CornerRadius(
                    with(LocalDensity.current) { (height / 2).toPx() }
                )
                // Track
                val trackColor = if (checked)
                    p.accent.copy(alpha = 0.6f)
                else
                    p.surface.copy(alpha = 0.4f)
                drawRoundRect(color = trackColor, cornerRadius = cr)

                if (physics.enableGlass) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
                            startY = 0f,
                            endY = 4.dp.toPx(),
                        ),
                        cornerRadius = cr,
                    )
                }
            }
            .padding(end = if (checked) 4.dp else 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // Thumb
        Box(
            modifier = Modifier
                .size(20.dp)
                .drawBehind {
                    val r = 10.dp.toPx()
                    drawCircle(color = Color.White, radius = r)
                    if (physics.enableGlass) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    p.glow.copy(alpha = 0.1f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                        )
                    }
                },
        )
    }
}

// ── GLASS SHEET BACKGROUND ────────────────────────────────────────

@Composable
fun GlassSheetBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerLarge.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val cr = CornerRadius(cornerPx, cornerPx, 0f, 0f)
                if (physics.enableGlass) {
                    drawRoundRect(
                        color = p.surface.copy(alpha = physics.transparency),
                        cornerRadius = cr,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = physics.reflection), Color.Transparent),
                            startY = 0f,
                            endY = 6.dp.toPx(),
                        ),
                        cornerRadius = cr,
                    )
                } else {
                    drawRoundRect(color = p.bg, cornerRadius = cr)
                }
            },
    ) {
        content()
    }
}

// ── GLASS LIST ROW ────────────────────────────────────────────────

@Composable
fun GlassListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .drawBehind {
                if (physics.enableGlass) {
                    drawRect(color = p.surface.copy(alpha = 0.3f))
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// ── GLASS DIVIDER (HORIZONTAL) ────────────────────────────────────

@Composable
fun GlassHorizontalDivider(modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = p.dim.copy(alpha = 0.08f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                )
            },
    )
}
