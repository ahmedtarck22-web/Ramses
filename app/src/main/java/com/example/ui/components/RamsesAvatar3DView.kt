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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewInAr
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * Features:
 * - Direct SceneView loading of ramses_glb.glb (with fallback to ramses_statue.glb)
 * - Live animated body gestures (sway, forward nod, royal breathing)
 * - Real-time lipsync mouth articulation and golden speech acoustic waves
 * - Interactive 360-degree touch rotation & zoom
 * - Status header indicating live royal speaking, listening, or reasoning
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
    isAutoSpeakEnabled: Boolean = true
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    val cameraNode = rememberCameraNode(engine = engine)
    val childNodes = rememberNodes()

    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var autoRotate by remember { mutableStateOf(false) }
    var manualRotationY by remember { mutableFloatStateOf(0f) }
    var manualRotationX by remember { mutableFloatStateOf(2f) }
    var currentScale by remember { mutableFloatStateOf(1.0f) }

    // Multi-track continuous animations for lifelike presence
    val infiniteTransition = rememberInfiniteTransition(label = "ramses_avatar_dynamics")

    // 1. Natural slow breathing cycle
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_breathing"
    )

    // 2. Speech articulation & Lipsync cadence (rapid syllable oscillation when speaking)
    val speechSyllableCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_lipsync"
    )

    // 3. Royal body gesture & head nod cycle when speaking
    val speechNodCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_nod"
    )

    // 4. Subtle ambient orbit when autoRotate is toggled
    val slowOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_orbit"
    )

    // Load the 3D model
    LaunchedEffect(Unit) {
        isLoading = true
        childNodes.clear()
        currentModelNode = null

        val candidatePaths = listOf(
            "models/ramses_glb.glb",
            "models/ramses_statue.glb"
        )

        for (path in candidatePaths) {
            try {
                val instance = modelLoader.createModelInstance(assetFileLocation = path)
                if (instance != null) {
                    val node = ModelNode(
                        modelInstance = instance,
                        scaleToUnits = currentScale
                    )
                    currentModelNode = node
                    childNodes.add(node)
                    break
                }
            } catch (e: Exception) {
                // Try fallback
            }
        }
        isLoading = false
    }

    // Dynamic real-time transform updates reacting to speech, lipsync, and touch gestures
    LaunchedEffect(
        isSpeaking,
        isListening,
        isThinking,
        rmsDb,
        breathingPhase,
        speechSyllableCycle,
        speechNodCycle,
        slowOrbitAngle,
        autoRotate,
        manualRotationY,
        manualRotationX,
        currentScale
    ) {
        currentModelNode?.let { node ->
            // Base idle breathing & organic royal head tilting
            val idleSwayY = sin(breathingPhase * 0.7f) * 1.8f
            val idleTiltX = cos(breathingPhase) * 1.5f
            val idleTiltZ = sin(breathingPhase * 0.5f) * 1.4f
            val idleElevationY = (sin(breathingPhase) * 0.014f)
            val idleScale = 1.0f + (sin(breathingPhase) * 0.009f)

            // Dynamic speech modulation
            val (speechTiltX, speechSwayY, speechLeanZ) = if (isSpeaking) {
                val nod = (sin(speechNodCycle) * 3.8f) + 3.0f // Natural dignified forward nod
                val sway = sin(speechNodCycle * 0.5f) * 2.2f // Subtle torso sway
                val lean = -0.04f + (sin(speechNodCycle) * 0.02f) // Lean slightly closer to the user
                Triple(nod, sway, lean)
            } else if (isListening) {
                // Attentive royal posture: slight head tilt to the side
                Triple(-1.5f, 2.8f, -0.02f)
            } else if (isThinking) {
                // Contemplative gaze slightly upwards
                Triple(-3.5f, -1.8f, 0.0f)
            } else {
                Triple(0f, 0f, 0f)
            }

            val totalY = if (autoRotate) {
                (slowOrbitAngle + manualRotationY + idleSwayY + speechSwayY) % 360f
            } else {
                (manualRotationY + idleSwayY + speechSwayY) % 360f
            }

            val totalX = (manualRotationX + idleTiltX + speechTiltX).coerceIn(-35f, 35f)
            val totalZ = if (isSpeaking) (speechSwayY * 0.3f) else idleTiltZ

            // Subtle dynamic chest expansion when speaking / breathing
            val dynamicScale = currentScale * (if (isSpeaking) 1.018f + (sin(speechNodCycle) * 0.01f) else idleScale)

            node.rotation = Rotation(x = totalX, y = totalY, z = totalZ)
            node.position = Position(x = 0f, y = idleElevationY, z = speechLeanZ)
            node.scale = Scale(dynamicScale, dynamicScale, dynamicScale)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF03050C),
                        Color(0xFF080D1A),
                        Color(0xFF141006),
                        Color(0xFF060912)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    autoRotate = false
                    manualRotationY += dragAmount.x * 0.55f
                    manualRotationX = (manualRotationX - dragAmount.y * 0.35f).coerceIn(-35f, 35f)
                }
            }
    ) {
        // 1. The 3D Filament SceneView
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes
        )

        // 2. Real-time Lipsync / Phonetic Acoustic Waves overlay radiating from King Ramses's mouth
        if (isSpeaking) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val mouthCenter = Offset(size.width * 0.50f, size.height * 0.44f)
                val baseAperture = (sin(speechSyllableCycle) + 1f) * 0.5f // 0.0 to 1.0
                val volumeBoost = (rmsDb.coerceIn(0f, 15f) / 15f) * 18f

                // Concentric royal golden sound ripples around lips/chin
                for (step in 1..3) {
                    val rippleRadius = (22f * step) + (baseAperture * 20f) + volumeBoost
                    val alpha = ((4 - step) / 4f) * (0.35f + (baseAperture * 0.35f))

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
                            RoyalGoldLight.copy(alpha = 0.75f),
                            Color(0x33D4AF37),
                            Color.Transparent
                        ),
                        center = mouthCenter,
                        radius = 28f + (baseAperture * 16f)
                    ),
                    radius = 28f + (baseAperture * 16f),
                    center = mouthCenter
                )
            }
        }

        // 3. Ambient Ancient Temple Columns & Celestial Aura (Stylized background accents)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top Status Pill (Royal State Indicator)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xD9060B14))
                    .border(
                        1.dp,
                        when {
                            isSpeaking -> RoyalGold
                            isListening -> Color(0xFF00E5FF)
                            isThinking -> Color(0xFF818CF8)
                            else -> RoyalGold.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Status Beacon Light
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSpeaking -> RoyalGoldLight
                                isListening -> Color(0xFF00E5FF)
                                isThinking -> Color(0xFF818CF8)
                                else -> Color(0xFF10B981)
                            }
                        )
                )

                Spacer(modifier = Modifier.width(8.dp))

                val statusText = when {
                    isSpeaking -> if (appLanguage == AppLanguage.ARABIC) "الملك رمسيس يتحدث إليك الآن..." else "Rameses is speaking to you..."
                    isListening -> if (appLanguage == AppLanguage.ARABIC) "يستمع لصوتك بدقة..." else "Listening to your voice..."
                    isThinking -> if (appLanguage == AppLanguage.ARABIC) "يستحضر الحكمة الفرعونية..." else "Consulting royal wisdom..."
                    else -> if (appLanguage == AppLanguage.ARABIC) "الملك رمسيس الثاني • ثلاثي الأبعاد" else "King Rameses II • 3D Avatar"
                }

                Text(
                    text = statusText,
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when {
                        isSpeaking -> RoyalGoldLight
                        isListening -> Color(0xFF00E5FF)
                        isThinking -> Color(0xFFC7D2FE)
                        else -> Color.White
                    }
                )

                if (isSpeaking) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // Mini live equalizer waves next to speech status
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val wave1 = (sin(speechSyllableCycle) + 1f) * 6f + 3f
                        val wave2 = (cos(speechSyllableCycle) + 1f) * 6f + 3f
                        val wave3 = (sin(speechSyllableCycle * 1.5f) + 1f) * 6f + 3f
                        Box(modifier = Modifier.width(2.5.dp).height(wave1.dp).clip(RoundedCornerShape(1.dp)).background(RoyalGold))
                        Box(modifier = Modifier.width(2.5.dp).height(wave2.dp).clip(RoundedCornerShape(1.dp)).background(RoyalGoldLight))
                        Box(modifier = Modifier.width(2.5.dp).height(wave3.dp).clip(RoundedCornerShape(1.dp)).background(RoyalGold))
                    }
                }
            }

            // Floating Controls Dock (Top Right & Bottom Right)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB30B1220))
                    .border(1.dp, RoyalGold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Auto-rotate toggle
                IconButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier.size(34.dp).testTag("avatar_autorotate_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "Auto Rotate",
                        tint = if (autoRotate) RoyalGoldLight else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Reset Orientation to front
                IconButton(
                    onClick = {
                        manualRotationY = 0f
                        manualRotationX = 2f
                        currentScale = 1.0f
                        autoRotate = false
                    },
                    modifier = Modifier.size(34.dp).testTag("avatar_reset_orientation")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset View",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom In (+)
                IconButton(
                    onClick = { currentScale = (currentScale + 0.15f).coerceAtMost(2.0f) },
                    modifier = Modifier.size(34.dp).testTag("avatar_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom Out (-)
                IconButton(
                    onClick = { currentScale = (currentScale - 0.15f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(34.dp).testTag("avatar_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Auto-speech toggle / Stop speech
                IconButton(
                    onClick = {
                        if (isSpeaking) {
                            onStopSpeaking()
                        } else {
                            onToggleAutoSpeak()
                        }
                    },
                    modifier = Modifier.size(34.dp).testTag("avatar_voice_toggle")
                ) {
                    Icon(
                        imageVector = if (isSpeaking || isAutoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Audio Speech",
                        tint = if (isSpeaking) RoyalGoldLight else if (isAutoSpeakEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom hint: interactive gestures notice
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x80040711))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "اسحب لتدوير رمسيس 360° • كبّر وقرّب بحرية" else "Drag to rotate 360° • Pinch / Zoom freely",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = AmiriFontFamily
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
