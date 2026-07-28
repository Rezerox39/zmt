package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════
// THEMED REUSABLE COMPONENTS
// All components read from LocalTuiColors for theme consistency.
// ═══════════════════════════════════════════════════════════════════

private fun TuiColorPalette.p() = this

// ── GLASS CARD ────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    val p = LocalTuiColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .drawBehind {
                drawRoundRect(color = p.card, cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(p.fg.copy(alpha = 0.08f), Color.Transparent),
                        startY = 0f, endY = size.height * 0.3f,
                    ),
                    size = Size(size.width, size.height * 0.3f),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, p.accent.copy(alpha = 0.04f)),
                        startY = size.height * 0.7f, endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height * 0.7f),
                    size = Size(size.width, size.height * 0.3f),
                )
                drawRoundRect(
                    color = p.line, style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                )
            }
    ) {
        content()
    }
}

// ── DROPLET BUTTON ────────────────────────────────────────────────

@Composable
fun DropletButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val p = LocalTuiColors.current
    val accent = accentColor ?: p.accent
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val glowAlpha = remember { Animatable(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btnScale",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRoundRect(color = p.card, cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()))
                drawRoundRect(
                    color = if (enabled) accent else p.faint,
                    style = Stroke(width = 1.5f),
                    cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                )
                if (isPressed) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                    )
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(p.fg.copy(alpha = 0.06f), Color.Transparent),
                    ), size = Size(size.width, size.height * 0.4f),
                )
            }
            .clickable(
                interactionSource = interactionSource, indication = null,
                enabled = enabled,
                onClick = {
                    scope.launch {
                        glowAlpha.snapTo(1f)
                        onClick()
                        glowAlpha.animateTo(0f, animationSpec = tween(300))
                    }
                },
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else p.faint,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── GLASS TILE ────────────────────────────────────────────────────

@Composable
fun GlassTile(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha by animateFloatAsState(
        targetValue = if (active) 0.25f else if (isPressed) 0.18f else 0.08f,
        animationSpec = tween(250),
        label = "tileBg",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(color = p.accent.copy(alpha = bgAlpha), cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                if (active) {
                    drawRoundRect(color = p.accent.copy(alpha = 0.3f), style = Stroke(width = 1.5f), cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                }
                drawRect(brush = Brush.verticalGradient(colors = listOf(p.fg.copy(alpha = 0.04f), Color.Transparent)), size = Size(size.width, size.height * 0.3f))
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(text = icon, style = MaterialTheme.typography.bodyLarge, color = if (active) p.accent else p.dim, modifier = Modifier.padding(end = 10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = if (active) p.accent else p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = p.dim, maxLines = 1)
                }
            }
        }
    }
}

// ── GLASS LIST ROW ────────────────────────────────────────────────

@Composable
fun GlassListRow(
    index: Int,
    line1: String,
    line2: String,
    current: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalTuiColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                if (current) {
                    drawRoundRect(color = p.accent.copy(alpha = 0.15f), cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                    drawRoundRect(color = p.accent.copy(alpha = 0.4f), style = Stroke(width = 1f), cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                } else if (isPressed) {
                    drawRoundRect(color = p.fg.copy(alpha = 0.05f), cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (current) "▶" else (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) p.accent else p.faint,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(28.dp), maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (current) "$line1  " else line1,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) p.accent else p.fg,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (line2.isNotEmpty()) {
                Text(
                    text = line2, style = MaterialTheme.typography.labelSmall,
                    color = if (current) p.accent.copy(alpha = 0.7f) else p.dim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) { trailing() }
    }
}

// ── GLASS PROGRESS BAR ────────────────────────────────────────────

@Composable
fun GlassProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = p.accent.copy(alpha = 0.9f),
        trackColor = p.faint,
        strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
    )
}

// ── GLASS SEARCH BAR ──────────────────────────────────────────────

@Composable
fun GlassSearchBar(
    query: String, hint: String, onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(color = p.card, cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))
                drawRoundRect(color = p.line, style = Stroke(width = 1f), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = query, onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.fg),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = p.dim)
                }
                inner()
            },
        )
    }
}

// ── GLASS TAB ─────────────────────────────────────────────────────

@Composable
fun GlassTab(
    label: String, active: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                if (active) {
                    drawRoundRect(color = p.accent.copy(alpha = 0.2f), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
                    drawRoundRect(color = p.accent.copy(alpha = 0.5f), style = Stroke(width = 1f), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label, style = MaterialTheme.typography.labelMedium,
            color = if (active) p.accent else p.dim,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── GLASS TOGGLE ──────────────────────────────────────────────────

@Composable
fun GlassToggle(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val fillColor = if (checked) p.accent.copy(alpha = 0.6f) else p.faint
                drawRoundRect(color = fillColor, cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                if (checked) {
                    drawRoundRect(color = p.accent.copy(alpha = 0.3f), style = Stroke(width = 1f), cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()))
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onCheckedChange(!checked) }),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .padding(4.dp)
                .drawBehind { drawCircle(color = if (checked) p.accent else p.dim, radius = 9.dp.toPx()) },
        )
    }
}

// ── AMBIENT GLOW DIVIDER ──────────────────────────────────────────

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val p = LocalTuiColors.current
    Box(modifier = modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(color = p.line) })
}

// ── THEMED BOTTOM NAV ─────────────────────────────────────────────

@Composable
fun GlassBottomNav(
    items: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(color = p.surface, cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()))
                drawRoundRect(color = p.line, style = Stroke(width = 0.5f), cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()))
            }
            .padding(vertical = 6.dp),
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val active = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onSelect(index) })
                    .padding(vertical = 4.dp),
            ) {
                Text(text = icon, style = MaterialTheme.typography.bodyLarge, color = if (active) p.accent else p.dim)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (active) p.accent else p.dim)
            }
        }
    }
}

// ── GLASS SHEET BACKGROUND ────────────────────────────────────────

@Composable
fun GlassSheetBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalTuiColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .drawBehind {
                drawRoundRect(color = p.surface, cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(p.fg.copy(alpha = 0.03f), Color.Transparent),
                        startY = 0f, endY = size.height * 0.08f,
                    ),
                    size = Size(size.width, size.height * 0.08f),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
}
