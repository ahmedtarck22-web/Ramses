package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlin.math.cos
import kotlin.math.sin
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@Composable
fun RamsesSceneView(
    modifier: Modifier = Modifier,
    modelAssetPath: String? = null,
    showControls: Boolean = true,
    initialAutoRotate: Boolean = false,
    isResponding: Boolean = false,
    idleAnimationEnabled: Boolean = true,
    rmsDb: Float = 0f
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    val cameraNode = rememberCameraNode(engine = engine)
    val childNodes = rememberNodes()

    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var autoRotate by remember { mutableStateOf(initialAutoRotate) }
    var manualRotationY by remember { mutableFloatStateOf(90f) }
    var manualRotationX by remember { mutableFloatStateOf(0f) }
    var currentScale by remember { mutableFloatStateOf(0.55f) }

    // Multi-track continuous animations for lifelike presence
    val infiniteTransition = rememberInfiniteTransition(label = "sceneview_ramses_dynamics")

    // 1. Natural slow breathing cycle (3.6s)
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing)
        ),
        label = "sceneview_breathing"
    )

    // 2. Subtle head tilt & curious gaze shift cycle (5.4s - desynced for organic feel)
    val headTiltPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing)
        ),
        label = "sceneview_head_tilt"
    )

    // 3. Active speech cadence nod when responding (1.3s)
    val speechCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing)
        ),
        label = "sceneview_speech_nod"
    )

    // 4. Turntable rotation when auto-rotate is toggled on
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing)
        ),
        label = "sceneview_orbit"
    )

    LaunchedEffect(modelAssetPath) {
        isLoading = true
        childNodes.clear()
        currentModelNode = null

        val candidatePaths = mutableListOf<String>()
        if (!modelAssetPath.isNullOrBlank()) {
            candidatePaths.add(modelAssetPath)
        }
        candidatePaths.add("models/Ramses.final.glb")

        for (path in candidatePaths.distinct()) {
            try {
                val instance = modelLoader.createModelInstance(assetFileLocation = path)
                val node = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = currentScale
                )
                node.centerOrigin(Position(0f, 0f, 0f))
                // If model contains embedded animations, trigger playback
                if (node.animationCount > 0) {
                    try {
                        node.playAnimation(0, loop = true)
                    } catch (_: Exception) {}
                }
                currentModelNode = node
                childNodes.add(node)
                break
            } catch (e: Exception) {
                // Try fallback
            }
        }
        isLoading = false
    }

    LaunchedEffect(
        animatedRotation,
        autoRotate,
        manualRotationY,
        manualRotationX,
        currentScale,
        breathingPhase,
        headTiltPhase,
        speechCycle,
        isResponding,
        idleAnimationEnabled,
        rmsDb
    ) {
        currentModelNode?.let { node ->
            if (idleAnimationEnabled) {
                if (isResponding) {
                    // Real 3D mouth lip sync blending live audio RMS amplitude with speech cadence
                    val cadence = ((sin(speechCycle * 4f) + 1f) * 0.5f)
                    val liveEnergy = (rmsDb / 12f).coerceIn(0f, 1f)
                    val mouthAperture = if (liveEnergy > 0.05f) {
                        (liveEnergy * 0.72f + cadence * 0.28f).coerceIn(0.12f, 1f)
                    } else {
                        cadence.coerceIn(0.12f, 1f)
                    }
                    val handToChin = (0.88f + (sin(speechCycle) * 0.12f)).coerceIn(0.70f, 1.0f)
                    try {
                        node.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                        node.renderableNodes.forEach {
                            it.setMorphWeights(floatArrayOf(mouthAperture, handToChin), 0)
                        }
                    } catch (_: Exception) {}

                    val idleElevationY = (sin(breathingPhase) * 0.004f)
                    val totalY = (manualRotationY + (if (autoRotate) animatedRotation else 0f)) % 360f

                    node.rotation = Rotation(x = 0.8f, y = totalY, z = 0f)
                    node.position = Position(x = 0f, y = idleElevationY, z = 0f)
                    node.scale = Scale(currentScale, currentScale, currentScale)
                } else {
                    try {
                        node.setMorphWeights(floatArrayOf(0f, 0f), 0)
                        node.renderableNodes.forEach {
                            it.setMorphWeights(floatArrayOf(0f, 0f), 0)
                        }
                    } catch (_: Exception) {}

                    val idleElevationY = (sin(breathingPhase) * 0.004f)
                    val totalY = (manualRotationY + (if (autoRotate) animatedRotation else 0f)) % 360f

                    node.rotation = Rotation(x = 0f, y = totalY, z = 0f)
                    node.position = Position(x = 0f, y = idleElevationY, z = 0f)
                    node.scale = Scale(currentScale, currentScale, currentScale)
                }
            } else {
                // Static turntable mode
                val totalY = if (autoRotate) {
                    (animatedRotation + manualRotationY) % 360f
                } else {
                    manualRotationY % 360f
                }
                node.rotation = Rotation(x = manualRotationX, y = totalY, z = 0f)
                node.position = Position(x = 0f, y = 0f, z = 0f)
                node.scale = Scale(currentScale, currentScale, currentScale)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1608),
                        Color(0xFF0E0C06),
                        Color(0xFF070603)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    autoRotate = false
                    manualRotationY += dragAmount.x * 0.6f
                    manualRotationX = (manualRotationX - dragAmount.y * 0.3f).coerceIn(-45f, 45f)
                }
            }
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color(0xFFD4AF37),
                    strokeWidth = 3.dp
                )
            }
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC18150D),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Out
                    IconButton(
                        onClick = {
                            currentScale = (currentScale - 0.2f).coerceAtLeast(0.4f)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sceneview_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = Color(0xFFE8C868),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Zoom In
                    IconButton(
                        onClick = {
                            currentScale = (currentScale + 0.2f).coerceAtMost(2.5f)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sceneview_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color(0xFFE8C868),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Auto-Rotate toggle
                    IconButton(
                        onClick = {
                            autoRotate = !autoRotate
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sceneview_autorotate_button")
                    ) {
                        Icon(
                            imageVector = if (autoRotate) Icons.Default.Autorenew else Icons.Default.PlayArrow,
                            contentDescription = if (autoRotate) "Pause Rotation" else "Auto Rotate",
                            tint = if (autoRotate) Color(0xFFD4AF37) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reset
                    IconButton(
                        onClick = {
                            manualRotationY = 90f
                            manualRotationX = 0f
                            currentScale = 0.55f
                            autoRotate = initialAutoRotate
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sceneview_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset View",
                            tint = Color(0xFFE8C868),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
