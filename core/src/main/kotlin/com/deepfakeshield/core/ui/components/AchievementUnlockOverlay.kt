package com.deepfakeshield.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AchievementUnlockOverlay(
    title: String,
    description: String,
    xpReward: Int,
    icon: ImageVector = Icons.Default.EmojiEvents,
    color: Color = Color(0xFFFFC107),
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { delay(5000); visible = false; onDismiss() }

    if (visible) {
        val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f), label = "scale")
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val glow by infiniteTransition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "g")

        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { visible = false; onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                Modifier.padding(32.dp).scale(scale),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
            ) {
                Column(
                    Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("\uD83C\uDF89", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("ACHIEVEMENT UNLOCKED", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.size(80.dp).background(color.copy(alpha = 0.15f * glow), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, Modifier.size(44.dp), tint = color)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                        Text("+$xpReward XP", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Tap to dismiss", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
                }
            }
        }
    }
}
