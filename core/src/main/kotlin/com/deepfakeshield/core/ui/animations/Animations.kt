package com.deepfakeshield.core.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 100x BETTER ANIMATIONS
 * Smooth, delightful micro-interactions
 */

// Pulse animation for status indicators
@Composable
fun rememberPulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    ).value
}

// Shimmer effect for loading states
@Composable
fun rememberShimmerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    ).value
}

// Bounce animation for buttons
@Composable
fun rememberBounceScale(pressed: Boolean): Float {
    return animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    ).value
}

// Smooth fade in animation
@Composable
fun AnimatedFadeIn(
    visible: Boolean = true,
    durationMillis: Int = 500,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis, delayMillis, FastOutSlowInEasing)
        ) + expandVertically(
            animationSpec = tween(durationMillis, delayMillis, FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis / 2, easing = FastOutLinearInEasing)
        ) + shrinkVertically(
            animationSpec = tween(durationMillis / 2, easing = FastOutLinearInEasing)
        )
    ) {
        content()
    }
}

// Slide in from side animation
@Composable
fun AnimatedSlideIn(
    visible: Boolean = true,
    direction: SlideDirection = SlideDirection.Left,
    durationMillis: Int = 400,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { if (direction == SlideDirection.Left) -it else it },
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis)),
        exit = slideOutHorizontally(
            targetOffsetX = { if (direction == SlideDirection.Left) -it else it },
            animationSpec = tween(durationMillis / 2, easing = FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis / 2))
    ) {
        content()
    }
}

enum class SlideDirection { Left, Right, Up, Down }

// Scale in animation
@Composable
fun AnimatedScaleIn(
    visible: Boolean = true,
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(durationMillis, delayMillis, FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis, delayMillis)
        ),
        exit = scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(durationMillis / 2, easing = FastOutLinearInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2)
        )
    ) {
        content()
    }
}

// Staggered list animation - uses simple alpha fade to avoid breaking Row/Column layouts
@Composable
fun StaggeredAnimation(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItem: Int = 50,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay((index * delayPerItem).toLong())
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "stagger_alpha"
    )
    
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        content()
    }
}

// Press animation modifier — caller must pass the same interactionSource used in Modifier.clickable()
fun Modifier.pressAnimation(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Shake animation for errors
@Composable
fun rememberShakeAnimation(trigger: Boolean): Float {
    val shake by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        finishedListener = {},
        label = "shake"
    )
    
    return when {
        shake < 0.2f -> shake * 5f * 10f
        shake < 0.4f -> (0.4f - shake) * 5f * 10f
        shake < 0.6f -> (shake - 0.4f) * 5f * 10f
        shake < 0.8f -> (0.8f - shake) * 5f * 10f
        else -> 0f
    }
}

// Rotate animation for refresh
@Composable
fun rememberRotationAnimation(rotating: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    return if (rotating) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        ).value
    } else {
        0f
    }
}

// Color pulse for critical alerts
@Composable
fun rememberAlertPulse(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    return infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    ).value
}

// ===== ENHANCED MICRO-ANIMATIONS =====

// Card press animation with depth effect — pass onClick to wire the interaction source
@Composable
fun AnimatedCardPress(
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_press"
    )
    val elevation by animateFloatAsState(
        targetValue = if (pressed) 2f else 8f,
        animationSpec = tween(150),
        label = "card_elevation"
    )
    
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .scale(scale)
            .graphicsLayer { shadowElevation = elevation }
    ) {
        content()
    }
}

// Counting animation for numbers
@Composable
fun AnimatedCounter(
    targetValue: Int,
    durationMillis: Int = 1000
): Int {
    var currentValue by remember { mutableStateOf(0) }
    
    LaunchedEffect(targetValue) {
        val startValue = currentValue
        val diff = targetValue - startValue
        val steps = 30
        val stepDelay = (durationMillis / steps).toLong()
        
        for (i in 1..steps) {
            delay(stepDelay)
            currentValue = startValue + ((diff.toLong() * i) / steps).toInt()
        }
        currentValue = targetValue
    }
    
    return currentValue
}

// Typewriter text animation
@Composable
fun AnimatedTypewriter(
    text: String,
    delayPerChar: Long = 30L,
    onComplete: () -> Unit = {}
): String {
    var displayText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayText = ""
        text.forEachIndexed { _, char ->
            delay(delayPerChar)
            displayText += char
        }
        onComplete()
    }
    
    return displayText
}

// Breathing animation (subtle size change)
@Composable
fun rememberBreathingAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    ).value
}

// Progress wave animation
@Composable
fun rememberWaveAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    ).value
}

// Slide up with fade animation for list items
@Composable
fun AnimatedSlideUp(
    visible: Boolean = true,
    index: Int = 0,
    delayPerItem: Int = 60,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(400, index * delayPerItem, FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(400, index * delayPerItem)
        ),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        content()
    }
}
