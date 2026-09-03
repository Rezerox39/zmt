package dev.abhi.zmt.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * ZMT Design Tokens — consistent spacing, shapes, and modifiers.
 */
object ZmtSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object ZmtRadius {
    val none = 0.dp   // terminal = no rounding
    val xs = 2.dp
    val sm = 4.dp
}

/**
 * Subtle press-down effect — scales to 0.97 with spring physics.
 * Use on interactive elements that need tactile feedback.
 */
fun Modifier.zmtPressEffect(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
