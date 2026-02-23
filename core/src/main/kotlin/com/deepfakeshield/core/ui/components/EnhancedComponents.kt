package com.deepfakeshield.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * 100x BETTER UI COMPONENTS
 * Modern, beautiful, delightful components
 */

// Enhanced gradient card with shimmer effect
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    ),
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = rememberBounceScale(isPressed)
    
    Card(
        modifier = modifier
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}

// Animated shield icon that pulses when active
@Composable
fun AnimatedShieldIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val pulseScale = if (isActive) rememberPulseAnimation() else 1f
    val color = if (isActive) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    
    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow effect
        if (isActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.toPx() / 2
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = radius
                )
            }
        }
        
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(size * 0.7f),
            tint = color
        )
    }
}

// Circular progress with percentage text
@Composable
fun CircularProgressWithText(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.toPx()
            val radius = diameter / 2
            val strokePx = strokeWidth.toPx()
            
            // Background circle
            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = radius - strokePx / 2,
                style = Stroke(width = strokePx)
            )
            
            // Progress arc
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                topLeft = Offset(strokePx / 2, strokePx / 2),
                size = androidx.compose.ui.geometry.Size(diameter - strokePx, diameter - strokePx)
            )
        }
        
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// Status indicator with pulse animation
@Composable
fun StatusIndicator(
    isActive: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val pulseScale = if (isActive) rememberPulseAnimation() else 1f
    val color = if (isActive) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

// Enhanced feature card with icon and gradient
@Composable
fun EnhancedFeatureCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedFadeIn {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated icon container
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.scale(if (enabled) rememberPulseAnimation() else 1f)) {
                        icon()
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SafeGreen,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }
    }
}

// Alert count badge with pulse
@Composable
fun AlertBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        val pulseAlpha = rememberAlertPulse()
        
        Box(
            modifier = modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SeverityCritical.copy(alpha = pulseAlpha))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Quick action button with icon
@Composable
fun QuickActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = rememberBounceScale(isPressed)
    
    AnimatedScaleIn {
        Card(
            modifier = modifier
                .scale(scale)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    }
                )
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}

// Threat level indicator with animation
@Composable
fun ThreatLevelIndicator(
    severity: RiskSeverity,
    score: Int,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (severity) {
        RiskSeverity.LOW -> SafeGreen to "Low Risk"
        RiskSeverity.MEDIUM -> WarningYellow to "Medium Risk"
        RiskSeverity.HIGH -> SeverityHigh to "High Risk"
        RiskSeverity.CRITICAL -> SeverityCritical to "Critical"
    }
    
    val isCritical = severity == RiskSeverity.CRITICAL || severity == RiskSeverity.HIGH
    val pulseAlpha = if (isCritical) rememberAlertPulse() else 1f
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Circular score indicator
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val diameter = size.width
                val strokeWidth = 16.dp.toPx()
                
                // Background circle
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = diameter / 2 - strokeWidth / 2,
                    style = Stroke(width = strokeWidth)
                )
                
                // Score arc
                drawArc(
                    color = color.copy(alpha = pulseAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
            }
        }
    }
}

// Security tool card with colored icon circle and label
@Composable
fun SecurityToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintColor: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = rememberBounceScale(isPressed)

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val resolvedBg = surfaceColor.copy(alpha = 0.5f).compositeOver(bgColor)

    Card(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = resolvedBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(16.dp)
        ) {
            if (badge != null) {
                Surface(
                    color = tintColor,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(tintColor.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(icon, null, tint = tintColor, modifier = Modifier.size(22.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tintColor.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Loading shimmer effect
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmer = rememberShimmerAnimation()
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    startX = shimmer * 1000,
                    endX = shimmer * 1000 + 500
                )
            )
    )
}
