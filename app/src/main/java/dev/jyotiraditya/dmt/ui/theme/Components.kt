package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import dev.jyotiraditya.dmt.domain.model.ThemeOption
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════
// AQUA GLASS COMPONENT SYSTEM
//
// Every component reads LocalTuiColors + LocalAquaPhysics at runtime,
// so switching themes instantly updates the entire UI.
// ═══════════════════════════════════════════════════════════════════

// ── helpers ──────────────────────────────────────────────────────

private fun DrawScope.glassHighlight(
    p: TuiColorPalette,
    cornerRadius: CornerRadius,
    highlightHeightPx: Float,
) {
    // Top-edge light reflection
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(p.highlight.copy(alpha = 0.15f), Color.Transparent),
            startY = 0f,
            endY = highlightHeightPx,
        ),
        cornerRadius = cornerRadius,
    )
    // Bottom ambient glow
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, p.glow.copy(alpha = 0.06f)),
            startY = size.height * 0.75f,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height * 0.75f),
        size = Size(size.width, size.height * 0.25f),
        cornerRadius = cornerRadius,
    )
}

// ── GLASS CARD ───────────────────────────────────────────────────
// Renders as a suspended water droplet on AMOLED black.
// Uses surface tension bulge, top-edge reflection, and internal glow.

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerSmall.toPx() }
    val cornerRadius = CornerRadius(cornerPx, cornerPx)
    val highlightPx = with(LocalDensity.current) { physics.highlightHeight.toPx() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1f - physics.rippleIntensity * 0.15f else 1f,
        animationSpec = spring<Float>(
            dampingRatio = physics.springDamping,
            stiffness = physics.springStiffness,
        ),
        label = "cardScale",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(physics.cornerSmall))
            .drawBehind {
                // Base glass surface
                drawRoundRect(color = p.card.copy(alpha = physics.transparency), cornerRadius = cornerRadius)

                // Internal refraction gradient
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(p.card.copy(alpha = 0.3f), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.6f,
                    ),
                    cornerRadius = cornerRadius,
                )

                // Glass highlight
                glassHighlight(p, cornerRadius, highlightPx)

                // Border
                drawRoundRect(
                    color = p.cardBorder.copy(alpha = 0.5f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = cornerRadius,
                )
            }
    ) {
        content()
    }
}

// ── DROPLET BUTTON ───────────────────────────────────────────────
// Floating pill-shaped button with glass shine.

@Composable
fun DropletButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerButton.toPx() }
    val cornerRadius = CornerRadius(cornerPx, cornerPx)
    val highlightPx = with(LocalDensity.current) { physics.highlightHeight.toPx() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring<Float>(
            dampingRatio = physics.springDamping,
            stiffness = physics.springStiffness,
        ),
        label = "btnScale",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(physics.cornerButton))
            .drawBehind {
                val bg = if (primary) p.accent.copy(alpha = if (enabled) 0.2f else 0.08f) else Color.Transparent
                drawRoundRect(color = bg, cornerRadius = cornerRadius)
                if (!primary) {
                    drawRoundRect(color = p.cardBorder, cornerRadius = cornerRadius, style = Stroke(1.dp.toPx()))
                }
                if (primary && enabled) {
                    glassHighlight(p, cornerRadius, highlightPx)
                }
                if (primary) {
                    drawRoundRect(
                        color = p.accent.copy(alpha = if (enabled) 0.5f else 0.2f),
                        cornerRadius = cornerRadius,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) p.accent else p.fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── GLASS LIST ROW ───────────────────────────────────────────────
// Track / item row with themed colours — no hardcoded Tui*.

@Composable
fun GlassListRow(
    index: Int,
    line1: String,
    line2: String,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalTuiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = (index + 1).toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) p.accent else p.faint,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line1,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) p.accent else p.fg,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line2.isNotEmpty()) {
                Text(
                    text = line2,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) p.accent.copy(alpha = 0.7f) else p.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

// ── LIQUID PROGRESS BAR ──────────────────────────────────────────
// Single liquid wave progress bar with gentle undulation.

@Composable
fun LiquidProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val infinite = rememberInfiniteTransition(label = "liquidBar")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 4f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = physics.wavePeriodMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val barHeight = size.height
        val barWidth = size.width
        val filledWidth = barWidth * fraction.coerceIn(0f, 1f)

        // Track
        drawRoundRect(
            color = p.faint,
            cornerRadius = CornerRadius(barHeight / 2, barHeight / 2),
            size = Size(barWidth, barHeight),
        )

        if (fraction > 0f) {
            // Liquid wave fill
            val wavePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, barHeight)
                for (x in 0..barWidth.toInt()) {
                    val wave = sin(x.toFloat() * 0.05f + wavePhase) * 0.5f
                    val y = (barHeight * 0.5f) + wave
                    lineTo(x.toFloat(), y)
                }
                lineTo(filledWidth, barHeight)
                close()
            }

            drawPath(
                wavePath,
                color = p.accent.copy(alpha = 0.85f),
            )

            // Gloss reflection on filled portion
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(p.highlight.copy(alpha = 0.1f), Color.Transparent),
                    startY = 0f,
                    endY = barHeight * 0.3f,
                ),
                size = Size(filledWidth, barHeight * 0.3f),
            )
        }
    }
}

// ── GLASS SEARCH BAR ─────────────────────────────────────────────
// Frosted glass capsule.

@Composable
fun GlassSearchBar(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerSearch.toPx() }
    val cornerRadius = CornerRadius(cornerPx, cornerPx)
    val highlightPx = with(LocalDensity.current) { physics.highlightHeight.toPx() }

    val isFocused = query.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(physics.cornerSearch))
            .drawBehind {
                drawRoundRect(
                    color = p.card.copy(alpha = physics.transparency * 0.9f),
                    cornerRadius = cornerRadius,
                )
                if (isFocused) {
                    drawRoundRect(
                        color = p.accent.copy(alpha = 0.15f),
                        cornerRadius = cornerRadius,
                    )
                }
                glassHighlight(p, cornerRadius, highlightPx)
                drawRoundRect(
                    color = if (isFocused) p.accent.copy(alpha = 0.4f) else p.cardBorder,
                    cornerRadius = cornerRadius,
                    style = Stroke(if (isFocused) 1.5f else 1f),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.fg),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.dim,
                    )
                }
                inner()
            },
        )
    }
}

// ── GLASS TOGGLE ─────────────────────────────────────────────────
// Neon cyber toggle — turns accent-coloured when active.

@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val fillColor = if (checked) p.accent.copy(alpha = 0.5f) else p.faint
                drawRoundRect(color = fillColor, cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                drawRoundRect(
                    color = if (checked) p.accent.copy(alpha = 0.3f) else p.cardBorder,
                    style = Stroke(width = 1f),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .padding(4.dp)
                .drawBehind {
                    drawCircle(
                        color = if (checked) p.accent else p.dim,
                        radius = 9.dp.toPx(),
                    )
                },
        )
    }
}

// ── GLASS DIVIDER ────────────────────────────────────────────────

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(color = p.line.copy(alpha = 0.6f)) },
    )
}

// ── GLASS TAB ────────────────────────────────────────────────────

@Composable
fun GlassTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(physics.cornerSmall / 2))
            .drawBehind {
                if (active) {
                    drawRoundRect(
                        color = p.accent.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(physics.cornerSmall.toPx() / 2, physics.cornerSmall.toPx() / 2),
                    )
                    drawRoundRect(
                        color = p.accent.copy(alpha = 0.4f),
                        style = Stroke(width = 1f),
                        cornerRadius = CornerRadius(physics.cornerSmall.toPx() / 2, physics.cornerSmall.toPx() / 2),
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) p.accent else p.dim,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── GLASS BOTTOM NAV ─────────────────────────────────────────────

@Composable
fun GlassBottomNav(
    items: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(physics.cornerSmall))
            .drawBehind {
                drawRoundRect(
                    color = p.surface,
                    cornerRadius = CornerRadius(physics.cornerSmall.toPx(), physics.cornerSmall.toPx()),
                )
                drawRoundRect(
                    color = p.cardBorder,
                    style = Stroke(width = 0.5f),
                    cornerRadius = CornerRadius(physics.cornerSmall.toPx(), physics.cornerSmall.toPx()),
                )
            }
            .padding(vertical = 6.dp),
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val active = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) p.accent else p.dim,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) p.accent else p.dim,
                )
            }
        }
    }
}

// ── GLASS SHEET BACKGROUND ───────────────────────────────────────

@Composable
fun GlassSheetBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerLarge.toPx() }
    val cornerRadius = CornerRadius(cornerPx, cornerPx)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = physics.cornerLarge, topEnd = physics.cornerLarge))
            .drawBehind {
                drawRoundRect(color = p.surface, cornerRadius = cornerRadius)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(p.highlight.copy(alpha = 0.03f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.08f,
                    ),
                    size = Size(size.width, size.height * 0.08f),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
}

// ── GLASS ALBUM ART FRAME ────────────────────────────────────────
// Wraps album art in a frosted water glass frame.

@Composable
fun GlassAlbumFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val physics = LocalAquaPhysics.current
    val cornerPx = with(LocalDensity.current) { physics.cornerAlbum.toPx() }
    val cornerRadius = CornerRadius(cornerPx, cornerPx)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(physics.cornerAlbum))
            .drawBehind {
                // Glass frame border
                drawRoundRect(color = p.cardBorder, cornerRadius = cornerRadius, style = Stroke(2.dp.toPx()))
                // Soft glow inside frame
                drawRoundRect(color = p.glow.copy(alpha = 0.08f), cornerRadius = cornerRadius)
                // Top reflection
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(p.highlight.copy(alpha = 0.1f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.15f,
                    ),
                    cornerRadius = cornerRadius,
                )
            },
    ) {
        content()
    }
}

// ── GLASS CAPSULE BADGE ──────────────────────────────────────────
// Liquid capsule for tags like "TG", "FLAC", "SYNCED".

@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val p = LocalTuiColors.current
    val clr = if (accent) p.accent else p.dim
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                drawRoundRect(
                    color = clr.copy(alpha = 0.15f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawRoundRect(
                    color = clr.copy(alpha = 0.3f),
                    style = Stroke(0.5f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
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

// ── GLASS CHIP ───────────────────────────────────────────────────

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

// ── THEMED CAPTION ───────────────────────────────────────────────

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

// ── ANIMATED THEME WRAPPER ──────────────────────────────────────
// Crossfade wrapper for theme switching.

@Composable
fun ThemeAnimatedContent(
    targetTheme: ThemeOption,
    content: @Composable () -> Unit,
) {
    val fadeAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "themeFade",
    )
    Box(modifier = Modifier.drawBehind { drawRect(color = Color.Black.copy(alpha = 1f - fadeAlpha)) }) {
        content()
    }
}

// ── FLOATING ANIMATION ───────────────────────────────────────────
// Adds a gentle vertical floating oscillation to any composable.
// Used for the mini player, album art (playing), and Telegram cloud card.

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

// ── WATER RIPPLE CANVAS ──────────────────────────────────────────
// Draws a water-ripple circle effect on a Canvas.
// Call from within a drawBehind block.

fun DrawScope.drawWaterRipple(
    centre: Offset,
    progress: Float,  // 0..1
    colour: androidx.compose.ui.graphics.Color,
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

// ── GLASS BACKGROUND GLOW ────────────────────────────────────────
// Draws a subtle radial glow behind a component.
// Call from within a drawBehind block.

fun DrawScope.drawGlassGlow(
    colour: androidx.compose.ui.graphics.Color,
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
