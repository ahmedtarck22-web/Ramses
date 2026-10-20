package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity 3D Avatar Viewport for King Ramses II.
 *
 * Implements user requirements:
 * - Direct SceneView loading of Ramses.final.glb exclusively.
 * - Perfectly centered full-body representation (scaled and centered so the complete figure from crown to feet is visible).
 * - Rotated by -90 degrees around Y so King Ramses faces forward directly at the user.
 * - No manual drag/rotation/zoom gestures.
 * - Prominent royal body language & head gestures when the AI speaks.
 * - Real-time acoustic lipsync waves radiating from King Ramses's mouth area.
 * - Deep navy/midnight pharaonic background.
 */
@Composable
fun RamsesAvatar3DView(
    modifier: Modifier = Modifier,
    isSpeaking: Boolean = false,
    isListening: Boolean = false,
    isThinking: Boolean = false,
    rmsDb: Float = 0f,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    onStopSpeaking: () -> Unit = {},
    onToggleAutoSpeak: () -> Unit = {},
    isAutoSpeakEnabled: Boolean = true,
    showBackgroundGradient: Boolean = true
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    val cameraNode = rememberCameraNode(engine = engine)
    val childNodes = rememberNodes()

    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Orientation rotated 180 degrees per user request (from 270f to 90f)
    val baseRotationY = 90f
    val baseScale = 0.54f

    // Multi-track continuous animations for lifelike presence
    val infiniteTransition = rememberInfiniteTransition(label = "ramses_avatar_dynamics")

    // 1. Natural slow breathing cycle
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_breathing"
    )

    // 2. Speech articulation & Lipsync cadence (rapid syllable oscillation when speaking)
    val speechSyllableCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 240, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_lipsync"
    )

    // 3. Royal hand-to-chin gesture & speech emphasis cycle (slow dignified cadence)
    val speechNodCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_nod"
    )

    // Load the 3D model: exclusively Ramses.final.glb
    LaunchedEffect(Unit) {
        isLoading = true
        childNodes.clear()
        currentModelNode = null

        val candidatePaths = listOf(
            "models/Ramses.final.glb"
        )

        for (path in candidatePaths) {
            try {
                val instance = modelLoader.createModelInstance(assetFileLocation = path)
                if (instance != null) {
                    val node = ModelNode(
                        modelInstance = instance,
                        scaleToUnits = baseScale
                    )
                    // Center the model's bounding box at (0, 0, 0) so the whole body is centered in viewport
                    node.centerOrigin(Position(0f, 0f, 0f))

                    // Initial front-facing posture (rotated 180 degrees to 270f)
                    node.rotation = Rotation(x = 0f, y = baseRotationY, z = 0f)
                    node.position = Position(x = 0f, y = -0.02f, z = 0f)
                    node.scale = Scale(baseScale, baseScale, baseScale)

                    // Initialize morph targets to 0
                    try {
                        node.setMorphWeights(floatArrayOf(0f, 0f), 0)
                        node.renderableNodes.forEach {
                            it.setMorphWeights(floatArrayOf(0f, 0f), 0)
                        }
                    } catch (_: Exception) {}

                    currentModelNode = node
                    childNodes.add(node)
                    break
                }
            } catch (e: Exception) {
                // Ignore and keep safe
            }
        }
        isLoading = false
    }

    // Dynamic real-time transform updates: mouth lip-sync, hand to chin, and majestic breathing
    LaunchedEffect(
        isSpeaking,
        isListening,
        isThinking,
        rmsDb,
        breathingPhase,
        speechSyllableCycle,
        speechNodCycle
    ) {
        currentModelNode?.let { node ->
            // Subtle, dignified breathing (gentle, majestic posture without shaking)
            val idleSwayY = sin(breathingPhase * 0.4f) * 0.4f
            val idleElevationY = -0.02f + (sin(breathingPhase) * 0.004f)

            // 1. Real 3D Mouth Lip-Sync: mouth physically opens & closes with speech syllables & volume
            val mouthAperture = if (isSpeaking) {
                val syllablePulse = (sin(speechSyllableCycle) + 1f) * 0.5f // 0.0 to 1.0
                val volumeBoost = (rmsDb.coerceIn(0f, 15f) / 15f) * 0.35f
                ((syllablePulse * 0.80f) + volumeBoost).coerceIn(0.12f, 1.0f)
            } else {
                0f
            }

            // 2. Real 3D Hand to Chin Gesture: right hand raises near chin when speaking or reasoning
            val handToChin = if (isSpeaking) {
                // Dignified hand gesture near chin, gently gesturing with speech cadence
                val nodCycle = sin(speechNodCycle) * 0.12f
                (0.88f + nodCycle).coerceIn(0.70f, 1.0f)
            } else if (isThinking) {
                // Contemplative hand resting thoughtfully near chin
                0.82f
            } else {
                // Resting comfortably
                0f
            }

            // Apply 3D Morph Weights directly to mesh vertices on GPU
            try {
                node.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                node.renderableNodes.forEach { rNode ->
                    rNode.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                }
            } catch (_: Exception) {}

            // Stable dignified posture: gentle royal stance, NO harsh shaking
            val speechForwardTilt = if (isSpeaking) 0.8f else 0f
            val totalY = (baseRotationY + idleSwayY) % 360f

            node.rotation = Rotation(x = speechForwardTilt, y = totalY, z = 0f)
            node.position = Position(x = 0f, y = idleElevationY, z = 0f)
            node.scale = Scale(baseScale, baseScale, baseScale)
        }
    }

    val containerModifier = if (showBackgroundGradient) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF040816),
                        Color(0xFF071126),
                        Color(0xFF0B1B3D),
                        Color(0xFF050B18)
                    )
                )
            )
    } else {
        modifier.fillMaxWidth()
    }

    Box(
        modifier = containerModifier
    ) {
        // 1. The 3D Filament SceneView
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes
        )

        // 2. Real-time Lipsync / Phonetic Acoustic Waves radiating from King Ramses's mouth area
        if (isSpeaking) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Model is centered; mouth is at approximately 35% height and 50% width
                val mouthCenter = Offset(size.width * 0.50f, size.height * 0.35f)
                val baseAperture = (sin(speechSyllableCycle) + 1f) * 0.5f // 0.0 to 1.0
                val volumeBoost = (rmsDb.coerceIn(0f, 15f) / 15f) * 22f

                // Concentric royal golden sound ripples around lips/chin
                for (step in 1..4) {
                    val rippleRadius = (20f * step) + (baseAperture * 22f) + volumeBoost
                    val alpha = ((5 - step) / 5f) * (0.40f + (baseAperture * 0.35f))

                    drawCircle(
                        color = RoyalGold.copy(alpha = alpha),
                        radius = rippleRadius,
                        center = mouthCenter,
                        style = Stroke(width = 2.5f)
                    )
                }

                // Golden speech glow dot at center of mouth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RoyalGoldLight.copy(alpha = 0.85f),
                            Color(0x55D4AF37),
                            Color.Transparent
                        ),
                        center = mouthCenter,
                        radius = 32f + (baseAperture * 18f)
                    ),
                    radius = 32f + (baseAperture * 18f),
                    center = mouthCenter
                )
            }
        }

        // 3. Royal Status Badge at top of avatar viewport
        // 3. Royal Status Badge at top of avatar viewport (Only active during speech or reasoning per user request)
        if (isSpeaking) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD9060B14))
                    .border(1.dp, RoyalGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = RoyalGoldLight,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "الملك رمسيس يتحدث إليك..." else "King Rameses is speaking...",
                    fontFamily = if (appLanguage == AppLanguage.ARABIC) AmiriFontFamily else CinzelFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalGoldLight
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Stop speech action
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onStopSpeaking() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "كتم الصوت",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else if (isThinking) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD9060B14))
                    .border(1.dp, EgyptianCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = EgyptianCyan,
                    strokeWidth = 2.dp
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "رمسيس يستحضر الحكمة..." else "Rameses is reasoning...",
                    fontFamily = if (appLanguage == AppLanguage.ARABIC) AmiriFontFamily else CinzelFontFamily,
                    fontSize = 12.sp,
                    color = EgyptianCyan
                )
            }
        }

        // Loading spinner
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = RoyalGold,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
