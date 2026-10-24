package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AvatarRigConfig
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * High-fidelity 3D Avatar Viewport for King Ramses II & Custom Rigged .GLB Models.
 *
 * Features:
 * - Loads custom user-imported .glb files (with full Skeletal Armature animation playback)
 *   or built-in Ramses.final.glb.
 * - Real-time GPU Morph Target lip-sync driven by Gemini Live 24kHz PCM audio RMS.
 * - Optional interactive Rig Skeleton Bone Lines ("خطوط الـ Rig") live overlay.
 * - Clipped graphicsLayer to prevent SurfaceView BLASTBufferQueue resize artifacts.
 */
@Composable
fun RamsesAvatar3DView(
    modifier: Modifier = Modifier,
    isSpeaking: Boolean = false,
    isListening: Boolean = false,
    isThinking: Boolean = false,
    rmsDb: Float = 0f,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    rigConfig: AvatarRigConfig = AvatarRigConfig(),
    onOpenRigStudio: (() -> Unit)? = null,
    onStopSpeaking: () -> Unit = {},
    onToggleAutoSpeak: () -> Unit = {},
    isAutoSpeakEnabled: Boolean = true,
    showBackgroundGradient: Boolean = true,
    verticalOffset: Float = 0f
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    val cameraNode = rememberCameraNode(engine = engine)
    val childNodes = rememberNodes()

    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val baseRotationY = rigConfig.modelRotationY
    val baseScale = rigConfig.modelScale

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
            animation = tween(durationMillis = 220, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_lipsync"
    )

    // 3. Royal hand-to-chin gesture & speech emphasis cycle
    val speechNodCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_nod"
    )

    // Reload 3D model whenever customModelPath changes
    LaunchedEffect(rigConfig.customModelPath) {
        isLoading = true
        childNodes.clear()
        currentModelNode = null

        var loadedInstance: io.github.sceneview.model.ModelInstance? = null

        // 1. Try loading user-imported custom .glb file first if configured
        val customPath = rigConfig.customModelPath
        if (!customPath.isNullOrBlank()) {
            try {
                val byteBuffer = withContext(Dispatchers.IO) {
                    val f = File(customPath)
                    if (f.exists() && f.length() > 16L) {
                        val bytes = f.readBytes()
                        ByteBuffer.allocateDirect(bytes.size)
                            .order(ByteOrder.nativeOrder())
                            .apply {
                                put(bytes)
                                rewind()
                            }
                    } else {
                        null
                    }
                }
                if (byteBuffer != null) {
                    loadedInstance = modelLoader.createModelInstance(byteBuffer)
                }
            } catch (_: Exception) {
                loadedInstance = null
            }
        }

        // 2. Fallback to built-in Ramses.final.glb
        if (loadedInstance == null) {
            try {
                loadedInstance = modelLoader.createModelInstance(assetFileLocation = "models/Ramses.final.glb")
            } catch (_: Exception) {
                loadedInstance = null
            }
        }

        if (loadedInstance != null) {
            try {
                val node = ModelNode(
                    modelInstance = loadedInstance,
                    scaleToUnits = baseScale
                )
                node.centerOrigin(Position(0f, 0f, 0f))
                node.rotation = Rotation(x = 0f, y = baseRotationY, z = 0f)
                node.position = Position(x = 0f, y = -0.02f + verticalOffset, z = 0f)
                node.scale = Scale(baseScale, baseScale, baseScale)

                // Play embedded glTF Skeletal Armature animations automatically if present (e.g. Mixamo / AccuRIG / Blender)
                try {
                    if (node.animationCount > 0) {
                        node.playAnimation(0, loop = true)
                    }
                } catch (_: Exception) {}

                // Initialize morph targets to 0 if available
                try {
                    node.setMorphWeights(floatArrayOf(0f, 0f), 0)
                    node.renderableNodes.forEach {
                        it.setMorphWeights(floatArrayOf(0f, 0f), 0)
                    }
                } catch (_: Exception) {}

                currentModelNode = node
                childNodes.add(node)
            } catch (_: Exception) {}
        }

        isLoading = false
    }

    // Dynamic real-time transform updates: mouth lip-sync, hand gesture, skeletal animation speed & majestic breathing
    LaunchedEffect(
        isSpeaking,
        isListening,
        isThinking,
        rmsDb,
        breathingPhase,
        speechSyllableCycle,
        speechNodCycle,
        baseRotationY,
        baseScale,
        rigConfig.lipSyncIntensity,
        rigConfig.gestureIntensity
    ) {
        currentModelNode?.let { node ->
            val idleSwayY = sin(breathingPhase * 0.4f) * 0.45f
            val idleElevationY = -0.02f + (sin(breathingPhase) * 0.004f)

            // 1. Real 3D Mouth Lip-Sync driven by Gemini Live audio RMS + syllable cadence
            val mouthAperture = if (isSpeaking) {
                val syllablePulse = (sin(speechSyllableCycle) + 1f) * 0.5f
                val normalizedRms = (rmsDb.coerceIn(0f, 15f) / 15f)
                val rawAperture = if (normalizedRms > 0.05f) {
                    (normalizedRms * 0.72f) + (syllablePulse * 0.35f)
                } else {
                    syllablePulse * 0.75f
                }
                (rawAperture * rigConfig.lipSyncIntensity).coerceIn(0.10f, 1.0f)
            } else {
                0f
            }

            // 2. Real 3D Hand Gesture
            val handToChin = if (isSpeaking) {
                val nodCycle = sin(speechNodCycle) * 0.12f
                ((0.86f + nodCycle) * rigConfig.gestureIntensity).coerceIn(0f, 1.0f)
            } else if (isThinking) {
                (0.80f * rigConfig.gestureIntensity).coerceIn(0f, 1.0f)
            } else {
                0f
            }

            try {
                node.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                node.renderableNodes.forEach { rNode ->
                    rNode.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                }
            } catch (_: Exception) {}

            val speechForwardTilt = if (isSpeaking) 0.9f * rigConfig.gestureIntensity.coerceAtMost(1.3f) else 0f
            val totalY = (baseRotationY + idleSwayY) % 360f

            node.rotation = Rotation(x = speechForwardTilt, y = totalY, z = 0f)
            node.position = Position(x = 0f, y = idleElevationY + verticalOffset, z = 0f)
            node.scale = Scale(baseScale, baseScale, baseScale)
        }
    }

    val containerModifier = if (showBackgroundGradient) {
        modifier
            .fillMaxWidth()
            .graphicsLayer { clip = true }
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
        modifier
            .fillMaxWidth()
            .graphicsLayer { clip = true }
    }

    val jawJoint = rigConfig.findJoint("jaw_mouth")

    Box(modifier = containerModifier) {
        // 1. The 3D Filament SceneView
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes
        )

        // 2. Live Rig Skeleton Bone Lines Overlay ("خطوط الـ Rig") if enabled by user
        if (rigConfig.showSkeletonLinesOnAvatar) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val breathDeltaY = sin(breathingPhase) * 3.5f
                val jawDeltaY = if (isSpeaking) {
                    val pulse = (sin(speechSyllableCycle) + 1f) * 0.5f
                    (pulse * 7f + (rmsDb.coerceIn(0f, 15f) / 15f) * 6f) * rigConfig.lipSyncIntensity
                } else 0f
                val handDeltaY = if (isSpeaking) -sin(speechNodCycle) * 10f * rigConfig.gestureIntensity else 0f

                fun animatedPoint(id: String): Offset? {
                    val j = rigConfig.joints.firstOrNull { it.id == id } ?: return null
                    val dy = when (id) {
                        "jaw_mouth" -> jawDeltaY + breathDeltaY * 0.5f
                        "hand_r" -> handDeltaY + breathDeltaY
                        "elbow_r" -> handDeltaY * 0.45f + breathDeltaY
                        "pelvis_root" -> 0f
                        else -> breathDeltaY
                    }
                    return Offset(j.x * w, (j.y * h) + dy)
                }

                for ((fromId, toId) in AvatarRigConfig.BONE_CONNECTIONS) {
                    val p1 = animatedPoint(fromId) ?: continue
                    val p2 = animatedPoint(toId) ?: continue
                    drawLine(
                        color = EgyptianCyan.copy(alpha = 0.30f),
                        start = p1,
                        end = p2,
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(RoyalGoldLight, EgyptianCyan),
                            start = p1,
                            end = p2
                        ),
                        start = p1,
                        end = p2,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                for (j in rigConfig.joints) {
                    val pt = animatedPoint(j.id) ?: continue
                    drawCircle(
                        color = if (j.id == "jaw_mouth") RoyalGoldLight else EgyptianCyan,
                        radius = if (j.id == "jaw_mouth") 6.5f else 5f,
                        center = pt
                    )
                    drawCircle(
                        color = PharaonicMidnight,
                        radius = 2.2f,
                        center = pt
                    )
                }
            }
        }

        // 3. Real-time Lipsync Acoustic Waves radiating from the configured Jaw/Mouth Rig Joint
        if (isSpeaking) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mouthX = jawJoint?.x ?: 0.50f
                val mouthY = jawJoint?.y ?: 0.35f
                val mouthCenter = Offset(size.width * mouthX, size.height * mouthY)
                val baseAperture = (sin(speechSyllableCycle) + 1f) * 0.5f
                val volumeBoost = (rmsDb.coerceIn(0f, 15f) / 15f) * 24f

                for (step in 1..4) {
                    val rippleRadius = (18f * step) + (baseAperture * 20f) + volumeBoost
                    val alpha = ((5 - step) / 5f) * (0.38f + (baseAperture * 0.35f))

                    drawCircle(
                        color = RoyalGold.copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = rippleRadius,
                        center = mouthCenter,
                        style = Stroke(width = 2.4f)
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RoyalGoldLight.copy(alpha = 0.85f),
                            Color(0x55D4AF37),
                            Color.Transparent
                        ),
                        center = mouthCenter,
                        radius = 30f + (baseAperture * 18f) + (volumeBoost * 0.5f)
                    ),
                    radius = 30f + (baseAperture * 18f) + (volumeBoost * 0.5f),
                    center = mouthCenter
                )
            }
        }

        // 4. Quick Rig Studio & .GLB File Import Shortcut Pill
        if (onOpenRigStudio != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x99081226))
                    .border(1.dp, RoyalGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable { onOpenRigStudio() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("open_rig_studio_pill")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = RoyalGoldLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.ARABIC) "الـ Rig + ملف 3D" else "Rig + .GLB",
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RoyalGoldLight
                    )
                }
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
