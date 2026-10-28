package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.AvatarRigConfig
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CancellationException
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
 * - Verifies GLES 3.0 + native Filament support before mounting `Engine`, falling back
 *   gracefully on unsupported/headless devices without crashing.
 * - Defers `Engine` creation by one frame and explicitly destroys `ModelNode` native
 *   entities prior to `engine.destroy()` to prevent concurrent EGL context exhaustion
 *   and `PANIC in filament::FEngine::shutdown`.
 * - Real-time GPU Morph Target lip-sync driven by Gemini Live 24kHz PCM audio RMS.
 * - Optional interactive Rig Skeleton Bone Lines ("خطوط الـ Rig") live overlay.
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
    pureBlackBackground: Boolean = false,
    sceneSlot: String = "main",
    verticalOffset: Float = 0f
) {
    val context = LocalContext.current
    val sharedResources = remember(context) {
        if (isFilament3DSupported(context)) getOrCreateSharedFilamentResources(context) else null
    }
    var mount3DEngine by remember { mutableStateOf(false) }
    var userRotationY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sharedResources) {
        if (sharedResources != null) {
            awaitSafeFilamentEngineMount()
            mount3DEngine = true
        }
    }

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

    val containerModifier = when {
        pureBlackBackground -> {
            modifier
                .fillMaxWidth()
                .background(Color.Black)
        }
        showBackgroundGradient -> {
            modifier
                .fillMaxWidth()
                .graphicsLayer { clip = true }
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Color.Black)
        }
        else -> {
            modifier
                .fillMaxWidth()
                .background(Color.Black)
        }
    }

    val jawJoint = rigConfig.findJoint("jaw_mouth")

    Box(
        modifier = containerModifier
            .testTag("ramses_3d_avatar_viewport")
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    userRotationY = (userRotationY + dragAmount * 0.55f) % 360f
                }
            }
    ) {
        // 1. The 3D Filament SceneView (or Royal 2D Fallback if GLES 3.0 / Filament unavailable)
        if (sharedResources != null) {
            if (mount3DEngine) {
                FilamentAvatarViewport(
                    shared = sharedResources,
                    isSpeaking = isSpeaking,
                    isThinking = isThinking,
                    rmsDb = rmsDb,
                    rigConfig = rigConfig,
                    breathingPhase = breathingPhase,
                    speechSyllableCycle = speechSyllableCycle,
                    speechNodCycle = speechNodCycle,
                    verticalOffset = verticalOffset,
                    userRotationY = userRotationY,
                    pureBlackBackground = pureBlackBackground,
                    sceneSlot = sceneSlot
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (pureBlackBackground) Modifier.background(Color.Black) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = RoyalGold,
                        strokeWidth = 3.dp
                    )
                }
            }
        } else {
            Pharaonic3DFallbackEmblem(
                modifier = Modifier.fillMaxSize(),
                appLanguage = appLanguage,
                pureBlackBackground = pureBlackBackground
            )
        }

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
    }
}

@Composable
private fun FilamentAvatarViewport(
    shared: SharedFilamentResources,
    isSpeaking: Boolean,
    isThinking: Boolean,
    rmsDb: Float,
    rigConfig: AvatarRigConfig,
    breathingPhase: Float,
    speechSyllableCycle: Float,
    speechNodCycle: Float,
    verticalOffset: Float,
    userRotationY: Float,
    pureBlackBackground: Boolean = false,
    sceneSlot: String = "main"
) {
    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val baseRotationY = rigConfig.modelRotationY
    val baseScale = rigConfig.modelScale

    DisposableEffect(Unit) {
        onDispose {
            currentModelNode = null
        }
    }

    // Load or retrieve cached 3D model node cleanly without destroying shared Filament assets
    LaunchedEffect(rigConfig.customModelPath) {
        isLoading = true
        currentModelNode = null

        var resolvedNode: ModelNode? = null

        // 1. Try loading user-imported custom .glb file first if configured
        val customPath = rigConfig.customModelPath
        if (!customPath.isNullOrBlank()) {
            try {
                val filePayload = withContext(Dispatchers.IO) {
                    val f = File(customPath)
                    if (f.exists() && f.length() in 17L..(64L * 1024L * 1024L)) {
                        val bytes = f.readBytes()
                        val buf = ByteBuffer.allocateDirect(bytes.size)
                            .order(ByteOrder.nativeOrder())
                            .apply {
                                put(bytes)
                                rewind()
                            }
                        f.lastModified() to buf
                    } else {
                        null
                    }
                }
                if (filePayload != null) {
                    resolvedNode = shared.getOrLoadCustomBufferNode(
                        customPath = customPath,
                        lastModified = filePayload.first,
                        byteBuffer = filePayload.second,
                        initialScale = baseScale,
                        slotKey = sceneSlot
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                resolvedNode = null
            }
        }

        // 2. Fallback to built-in Ramses.final.glb (instant 0ms cache hit after first load)
        if (resolvedNode == null) {
            resolvedNode = shared.getOrLoadAssetNode(
                assetPath = "models/Ramses.final.glb",
                initialScale = baseScale,
                slotKey = sceneSlot
            )
        }

        if (resolvedNode != null) {
            try {
                resolvedNode.rotation = Rotation(
                    x = 0f,
                    y = (baseRotationY + userRotationY) % 360f,
                    z = 0f
                )
                resolvedNode.position = Position(x = 0f, y = -0.02f + verticalOffset, z = 0f)
                resolvedNode.scale = Scale(baseScale, baseScale, baseScale)
                safeApplyMorphWeights(shared.engine, resolvedNode, floatArrayOf(0f, 0f))
                currentModelNode = resolvedNode
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {}
        }

        isLoading = false
    }

    // Dynamic real-time transform updates: mouth lip-sync, hand gesture, and horizontal-only rotation (no up/down tilt)
    LaunchedEffect(
        currentModelNode,
        isSpeaking,
        isThinking,
        rmsDb,
        breathingPhase,
        speechSyllableCycle,
        speechNodCycle,
        baseRotationY,
        userRotationY,
        baseScale,
        rigConfig.lipSyncIntensity,
        rigConfig.gestureIntensity,
        verticalOffset
    ) {
        currentModelNode?.let { node ->
            val idleSwayY = sin(breathingPhase * 0.4f) * 0.45f

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

            val handToChin = if (isSpeaking) {
                val nodCycle = sin(speechNodCycle) * 0.12f
                ((0.86f + nodCycle) * rigConfig.gestureIntensity).coerceIn(0f, 1.0f)
            } else if (isThinking) {
                (0.80f * rigConfig.gestureIntensity).coerceIn(0f, 1.0f)
            } else {
                0f
            }

            safeApplyMorphWeights(shared.engine, node, floatArrayOf(mouthAperture, handToChin))

            try {
                val totalY = (baseRotationY + userRotationY + idleSwayY) % 360f
                node.rotation = Rotation(x = 0f, y = totalY, z = 0f)
                node.position = Position(x = 0f, y = -0.02f + verticalOffset, z = 0f)
                node.scale = Scale(baseScale, baseScale, baseScale)
            } catch (_: Throwable) {}
        }
    }

    val activeNodes = remember(currentModelNode) {
        currentModelNode?.let { listOf(it) } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (pureBlackBackground) Modifier.background(Color.Black) else Modifier)
    ) {
        SafeFilamentScene(
            modifier = Modifier.fillMaxSize(),
            shared = shared,
            childNodes = activeNodes,
            pureBlackBackground = pureBlackBackground
        )

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
