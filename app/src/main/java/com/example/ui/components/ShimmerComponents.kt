package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, translateAnim * 0.3f),
        end = Offset(translateAnim + 340f, (translateAnim + 340f) * 0.3f)
    )
}

@Composable
fun ArticleReaderShimmer(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reader_shimmer_skeleton"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val widths = listOf(0.96f, 1.0f, 0.92f, 0.78f)
        repeat(3) { paragraphIdx ->
            widths.forEachIndexed { idx, fraction ->
                val adjusted = if (paragraphIdx == 2 && idx == 3) 0.55f else fraction
                ShimmerBar(brush = brush, fraction = adjusted, height = 16.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun TelegramDraftShimmer(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerBar(brush = brush, fraction = 0.95f, height = 15.dp)
        ShimmerBar(brush = brush, fraction = 1.0f, height = 15.dp)
        ShimmerBar(brush = brush, fraction = 0.88f, height = 15.dp)
        ShimmerBar(brush = brush, fraction = 0.64f, height = 15.dp)
    }
}

@Composable
private fun ShimmerBar(brush: Brush, fraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(brush)
    )
}
