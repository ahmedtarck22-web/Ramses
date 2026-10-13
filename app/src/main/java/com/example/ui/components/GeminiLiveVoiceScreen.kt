package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VoiceCallState
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeminiLiveFluidVisualizer(
    state: VoiceCallState,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_waves")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_anim"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(300.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.35f * (if (state == VoiceCallState.SPEAKING) pulseScale * 1.05f else pulseScale)

            // Dynamic audio amplification
            val audioBoost = if (state == VoiceCallState.LISTENING) (rmsDb.coerceIn(0f, 15f) / 15f) * 35f else 10f

            // Layer 1: Ambient outer aurora glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4285F4).copy(alpha = 0.35f),
                        Color(0xFF00E5FF).copy(alpha = 0.25f),
                        Color(0xFFE5B842).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f + audioBoost
                ),
                radius = baseRadius * 1.55f + audioBoost,
                center = center
            )

            // Layer 2: Organic morphing fluid blob (Gemini Live style)
            val points = 8
            val path = Path()
            val angleStep = (2f * Math.PI.toFloat()) / points

            for (i in 0 until points) {
                val currentAngle = i * angleStep + (rotation * (Math.PI.toFloat() / 180f))
                val waveOffset = sin(currentAngle * 3f + wavePhase) * (18f + audioBoost)
                val r = baseRadius + waveOffset

                val x = center.x + r * cos(currentAngle)
                val y = center.y + r * sin(currentAngle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevAngle = (i - 1) * angleStep + (rotation * (Math.PI.toFloat() / 180f))
                    val prevR = baseRadius + sin(prevAngle * 3f + wavePhase) * (18f + audioBoost)
                    val prevX = center.x + prevR * cos(prevAngle)
                    val prevY = center.y + prevR * sin(prevAngle)

                    val cx = (prevX + x) / 2f
                    val cy = (prevY + y) / 2f
                    path.quadraticTo(prevX, prevY, cx, cy)
                }
            }
            path.close()

            // Colors shifting between Gemini Royal Blue, Electric Cyan, and Pharaonic Gold
            val blobBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF38BDF8), // Sky Blue
                    Color(0xFF818CF8), // Indigo
                    Color(0xFFE5B842), // Royal Gold
                    Color(0xFF00E5FF)  // Electric Cyan
                ),
                start = Offset(center.x - baseRadius, center.y - baseRadius),
                end = Offset(center.x + baseRadius, center.y + baseRadius)
            )

            drawPath(path = path, brush = blobBrush)

            // Inner core bright jewel reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color(0xFFE5B842).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.45f
                ),
                radius = baseRadius * 0.45f,
                center = center
            )
        }
    }
}

@Composable
fun GeminiLiveVoiceScreen(
    voiceCallState: VoiceCallState,
    lastTranscript: String,
    lastRamsesSpeech: String,
    rmsDb: Float = 0f,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEndCall: () -> Unit
) {
    Dialog(
        onDismissRequest = onEndCall,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06090E))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Gemini Live Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("gemini_live_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EgyptianCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gemini Live • اسأل رمسيس",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x26FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "عربي",
                            fontFamily = AmiriFontFamily,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Center Gemini Live Fluid Visualizer
                GeminiLiveFluidVisualizer(
                    state = voiceCallState,
                    rmsDb = rmsDb
                )

                // Live Transcript & Status Pill
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusText = when (voiceCallState) {
                        VoiceCallState.LISTENING -> "يستمع إليك الآن..."
                        VoiceCallState.THINKING -> "رمسيس يتأمل..."
                        VoiceCallState.SPEAKING -> "رمسيس يتحدث مباشرة..."
                        VoiceCallState.IDLE -> "انقر على الميكروفون للتحدث"
                    }

                    Text(
                        text = statusText,
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = when (voiceCallState) {
                            VoiceCallState.LISTENING -> EgyptianCyan
                            VoiceCallState.SPEAKING -> RoyalGoldLight
                            VoiceCallState.THINKING -> RoyalGold
                            VoiceCallState.IDLE -> TextSecondary
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x1AFFFFFF)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayText = if (voiceCallState == VoiceCallState.SPEAKING && lastRamsesSpeech.isNotBlank()) {
                                lastRamsesSpeech
                            } else if (lastTranscript.isNotBlank()) {
                                "« $lastTranscript »"
                            } else {
                                "تحدث معي بحرية عن أي موضوع، تاريخي أو علمي أو حياتي، وسأجيبك صوتياً فوراً."
                            }

                            Text(
                                text = displayText,
                                fontFamily = AmiriFontFamily,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Minimal Gemini Live Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Speaker toggle
                    IconButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0x1FFFFFFF))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "كتم الصوت",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Main Mic Action (Live)
                    IconButton(
                        onClick = {
                            if (voiceCallState == VoiceCallState.LISTENING) {
                                onStopListening()
                            } else {
                                onStartListening()
                            }
                        },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                if (voiceCallState == VoiceCallState.LISTENING) EgyptianCyan else Color.White
                            )
                            .testTag("gemini_live_mic_action")
                    ) {
                        Icon(
                            imageVector = if (voiceCallState == VoiceCallState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "تحدث",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // End Call Button
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .testTag("gemini_live_end_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "إنهاء المكالمة",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
