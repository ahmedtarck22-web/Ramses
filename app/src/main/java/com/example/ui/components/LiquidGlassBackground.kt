package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.PharaonicMidnight

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_transition")

    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob_float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PharaonicMidnight)
    ) {
        // Single tone deep royal blue & gold ambient liquid glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Blob 1: Deep Navy/Lapis liquid glow (matching HTML blob-dark)
            val blob1CenterX = width * (0.85f - animProgress * 0.15f)
            val blob1CenterY = height * (0.75f - animProgress * 0.1f)
            val blob1Radius = width * (0.75f + animProgress * 0.1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF102A43).copy(alpha = 0.55f),
                        Color(0xFF0D1B2A).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(blob1CenterX, blob1CenterY),
                    radius = blob1Radius
                ),
                radius = blob1Radius,
                center = Offset(blob1CenterX, blob1CenterY)
            )

            // Blob 2: Subtle golden royal aura near the upper crest
            val blob2CenterX = width * (0.2f + animProgress * 0.1f)
            val blob2CenterY = height * (0.2f + animProgress * 0.08f)
            val blob2Radius = width * 0.55f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE5B842).copy(alpha = 0.12f),
                        Color(0xFF00E5FF).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(blob2CenterX, blob2CenterY),
                    radius = blob2Radius
                ),
                radius = blob2Radius,
                center = Offset(blob2CenterX, blob2CenterY)
            )
        }

        content()
    }
}
