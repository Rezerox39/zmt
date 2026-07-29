package dev.jyotiraditya.dmt.core.common

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.ui.theme.TuiAccent
import dev.jyotiraditya.dmt.ui.theme.LocalTuiColors
import dev.jyotiraditya.dmt.ui.theme.TuiBg
import dev.jyotiraditya.dmt.ui.theme.TuiBright
import dev.jyotiraditya.dmt.ui.theme.TuiDim
import dev.jyotiraditya.dmt.ui.theme.TuiFaint
import dev.jyotiraditya.dmt.ui.theme.TuiFg
import dev.jyotiraditya.dmt.ui.theme.TuiLine
import dev.jyotiraditya.dmt.ui.theme.TuiSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun TuiNotice(
    error: String?,
    notice: String?,
    modifier: Modifier = Modifier,
    reserveSpace: Boolean = false,
) {
    val p = LocalTuiColors.current
    val text = error ?: notice
    if (text == null && !reserveSpace) return
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        color = if (error != null) MaterialTheme.colorScheme.error else p.dim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@Composable
fun CursorTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val cursorAlpha = rememberCursorAlpha()
    Text(
        text = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = p.accent.copy(alpha = cursorAlpha))) {
                append("_")
            }
        },
        style = style,
        color = p.bright,
        maxLines = 1,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
    )
}

@Composable
fun ScrollMemory(key: Any, content: @Composable () -> Unit) {
    rememberSaveableStateHolder().SaveableStateProvider(key, content)
}

@Composable
fun rememberCursorAlpha(periodMs: Int = 1060): Float {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMs
                1f at 0
                1f at periodMs / 2
                0f at periodMs / 2 + 1
                0f at periodMs
            },
        ),
        label = "cursorAlpha",
    )
    return alpha
}

@Composable
fun FitScaled(fitScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * fitScale, density.fontScale),
        content = content,
    )
}

private class PressFlash(private val scope: CoroutineScope) {
    private val flash = Animatable(0f)

    val value: Float
        get() = flash.value

    fun click() {
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(220))
        }
    }
}

@Composable
private fun rememberPressFlash(): PressFlash {
    val scope = rememberCoroutineScope()
    return remember { PressFlash(scope) }
}

private class TuiPress(
    val interactionSource: MutableInteractionSource,
    private val flash: PressFlash,
    private val isPressed: State<Boolean>,
) {
    val fraction: Float
        get() = if (isPressed.value) 1f else flash.value

    fun click(action: () -> Unit) {
        flash.click()
        action()
    }
}

@Composable
private fun rememberTuiPress(): TuiPress {
    val interactionSource = remember { MutableInteractionSource() }
    return TuiPress(
        interactionSource = interactionSource,
        flash = rememberPressFlash(),
        isPressed = interactionSource.collectIsPressedAsState(),
    )
}

fun Modifier.tuiClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
    )

@Composable
fun TuiPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalTuiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, p.line)
            .background(p.surface.copy(alpha = 0.85f))
            .let { if (onClick != null) it.tuiClickable(onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun TuiKey(
    label: String,
    modifier: Modifier = Modifier,
    bright: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    val press = rememberTuiPress()
    val restText = if (bright) p.bg else p.fg
    val restBorder = if (bright) p.fg else p.line
    val restBg = if (bright) p.fg else p.surface.copy(alpha = 0.4f)
    val pressText = if (bright) p.fg else p.bg
    val pressBorder = if (bright) p.line else p.fg
    val pressBg = if (bright) p.surface.copy(alpha = 0.4f) else p.fg
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = lerp(restText, pressText, press.fraction),
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, lerp(restBorder, pressBorder, press.fraction))
            .background(lerp(restBg, pressBg, press.fraction))
            .clickable(
                interactionSource = press.interactionSource,
                indication = null,
            ) {
                press.click(onClick)
            }
            .padding(
                horizontal = if (big) 22.dp else 14.dp,
                vertical = if (big) 15.dp else 11.dp,
            ),
    )
}

@Composable
fun TuiTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) p.bg else p.dim,
        modifier = modifier
            .border(1.dp, if (active) p.fg else p.line)
            .background(if (active) p.fg else p.surface.copy(alpha = 0.4f))
            .tuiClickable(onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun TuiChip(text: String) {
    val p = LocalTuiColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = p.dim,
        modifier = Modifier
            .border(1.dp, p.line)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
fun TuiStatus(
    label: String,
    value: String,
    on: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalTuiColors.current
    val press = rememberTuiPress()
    val restText = if (on) p.bright else p.dim
    val blink = if (busy) rememberCursorAlpha() else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, lerp(p.line, p.fg, press.fraction))
            .background(lerp(p.surface.copy(alpha = 0.4f), p.fg, press.fraction))
            .clickable(
                interactionSource = press.interactionSource,
                indication = null,
            ) {
                press.click(onClick)
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    when {
                        busy -> p.accent.copy(alpha = blink)
                        on -> p.accent
                        else -> p.faint
                    },
                ),
        )
        Text(
            text = " $label:$value",
            style = MaterialTheme.typography.labelMedium,
            color = lerp(restText, p.bg, press.fraction),
        )
    }
}

@Composable
fun Hairline(fraction: Float, modifier: Modifier = Modifier) {
    dev.jyotiraditya.dmt.ui.theme.LiquidProgressBar(
        fraction = fraction,
        modifier = modifier,
        height = 2.dp,
    )
}

@Composable
fun ThinSlider(
    fraction: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalTuiColors.current
    val filledColor = p.fg
    val emptyColor = p.faint
    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textSize = 28f
        }
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    },
                    onDragEnd = { onSeek(last) },
                    onDragCancel = { onScrub(null) },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val advance = paint.measureText("█")
            val cols = (size.width / advance).toInt().coerceAtLeast(1)
            val filledCols = (cols * fraction.coerceIn(0f, 1f)).toInt()
            val baseline = size.height / 2 + paint.textSize * 0.35f
            drawContext.canvas.nativeCanvas.apply {
                for (col in 0 until cols) {
                    val filled = col < filledCols
                    val glyph = if (filled) "█" else "░"
                    paint.color = when {
                        col == filledCols - 1 -> p.accent.toArgb()
                        filled -> filledColor.toArgb()
                        else -> emptyColor.toArgb()
                    }
                    drawText(
                        glyph,
                        col * advance,
                        baseline,
                        paint,
                    )
                }
            }
        }
    }
}
