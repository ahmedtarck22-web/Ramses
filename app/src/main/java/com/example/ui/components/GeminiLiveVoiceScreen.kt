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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.VoiceCallState
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RoyalGeminiHologramVisualizer(
    state: VoiceCallState,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hologram_visualizer")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(310.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val audioBoost = if (state == VoiceCallState.LISTENING) {
                (rmsDb.coerceIn(0f, 15f) / 15f) * 45f
            } else if (state == VoiceCallState.SPEAKING) {
                25f
            } else {
                8f
            }

            val baseRadius = size.width * 0.32f * (if (state == VoiceCallState.SPEAKING) breathingScale * 1.06f else breathingScale)

            // 1. Ambient Outer Nebula
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        when (state) {
                            VoiceCallState.LISTENING -> EgyptianCyan.copy(alpha = 0.35f)
                            VoiceCallState.SPEAKING -> RoyalGoldLight.copy(alpha = 0.40f)
                            VoiceCallState.THINKING -> Color(0xFF818CF8).copy(alpha = 0.35f)
                            VoiceCallState.IDLE -> RoyalGold.copy(alpha = 0.15f)
                        },
                        Color(0xFF0F172A).copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.65f + audioBoost
                ),
                radius = baseRadius * 1.65f + audioBoost,
                center = center
            )

            // 2. Outer Celestial Orbital Ring with Orbiting Nodes
            drawCircle(
                color = when (state) {
                    VoiceCallState.LISTENING -> EgyptianCyan.copy(alpha = 0.4f)
                    VoiceCallState.SPEAKING -> RoyalGold.copy(alpha = 0.5f)
                    VoiceCallState.THINKING -> Color(0xFF818CF8).copy(alpha = 0.4f)
                    VoiceCallState.IDLE -> Color.White.copy(alpha = 0.2f)
                },
                radius = baseRadius * 1.35f + audioBoost * 0.5f,
                center = center,
                style = Stroke(width = 2f)
            )

            // Orbiting celestial nodes
            val numNodes = 6
            for (i in 0 until numNodes) {
                val nodeAngle = (i * (2 * Math.PI / numNodes) + Math.toRadians(outerRotation.toDouble())).toFloat()
                val nodeR = baseRadius * 1.35f + audioBoost * 0.5f
                val nodeX = center.x + nodeR * cos(nodeAngle)
                val nodeY = center.y + nodeR * sin(nodeAngle)
                drawCircle(
                    color = if (i % 2 == 0) RoyalGold else EgyptianCyan,
                    radius = 4.5f,
                    center = Offset(nodeX, nodeY)
                )
            }

            // 3. Fluid Living Plasma Waveform (Pharaonic Gemini Live style)
            val points = 12
            val path = Path()
            val angleStep = (2f * Math.PI.toFloat()) / points

            for (i in 0 until points) {
                val currentAngle = i * angleStep + Math.toRadians(innerRotation.toDouble()).toFloat()
                val waveOffset = sin(currentAngle * 4f + wavePhase) * (14f + audioBoost)
                val r = baseRadius + waveOffset

                val x = center.x + r * cos(currentAngle)
                val y = center.y + r * sin(currentAngle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevAngle = (i - 1) * angleStep + Math.toRadians(innerRotation.toDouble()).toFloat()
                    val prevR = baseRadius + sin(prevAngle * 4f + wavePhase) * (14f + audioBoost)
                    val prevX = center.x + prevR * cos(prevAngle)
                    val prevY = center.y + prevR * sin(prevAngle)

                    val cx = (prevX + x) / 2f
                    val cy = (prevY + y) / 2f
                    path.quadraticTo(prevX, prevY, cx, cy)
                }
            }
            path.close()

            val plasmaBrush = Brush.linearGradient(
                colors = when (state) {
                    VoiceCallState.LISTENING -> listOf(
                        Color(0xFF00E5FF),
                        Color(0xFF38BDF8),
                        Color(0xFF0284C7),
                        Color(0xFFE5B842)
                    )
                    VoiceCallState.SPEAKING -> listOf(
                        Color(0xFFFFDF7A),
                        Color(0xFFE5B842),
                        Color(0xFFF59E0B),
                        Color(0xFF00E5FF)
                    )
                    VoiceCallState.THINKING -> listOf(
                        Color(0xFF818CF8),
                        Color(0xFFC084FC),
                        Color(0xFFE5B842),
                        Color(0xFF6366F1)
                    )
                    VoiceCallState.IDLE -> listOf(
                        Color(0xFF1E293B),
                        Color(0xFF334155),
                        Color(0xFFE5B842).copy(alpha = 0.5f),
                        Color(0xFF1E293B)
                    )
                },
                start = Offset(center.x - baseRadius, center.y - baseRadius),
                end = Offset(center.x + baseRadius, center.y + baseRadius)
            )

            drawPath(path = path, brush = plasmaBrush)

            // 4. Inner Luminous Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        RoyalGoldLight.copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.45f
                ),
                radius = baseRadius * 0.45f,
                center = center
            )
        }

        // Center Pharaonic Emblem
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color(0xD9060B14))
                .border(2.dp, RoyalGold, CircleShape)
        ) {
            Text(
                text = "𓇳", // Ra Sun Hieroglyph
                fontSize = 32.sp,
                color = RoyalGoldLight
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
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    onToggleLanguage: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onEndCall: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }

    val quickSuggestions = remember(appLanguage) {
        if (appLanguage == AppLanguage.ARABIC) {
            listOf(
                "حدثني عن معركة قادش",
                "من بنى معبد أبو سمبل؟",
                "أعطني حكمة ملكية لليوم",
                "كيف أنظم وقتي وأهدافي؟",
                "ما سر تعامد الشمس في أبو سمبل؟"
            )
        } else {
            listOf(
                "Tell me about the Battle of Kadesh",
                "Who built the temples of Abu Simbel?",
                "Give me royal wisdom for today",
                "How can I master discipline and focus?",
                "What is the story of your peace treaty?"
            )
        }
    }

    Dialog(
        onDismissRequest = onEndCall,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF030712),
                            Color(0xFF0B1329),
                            Color(0xFF020617)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header: Close, Title, Live indicator & Language Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                            .testTag("gemini_live_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Live Pill & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x22102A43))
                            .border(1.dp, RoyalGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (voiceCallState) {
                                        VoiceCallState.LISTENING -> EgyptianCyan
                                        VoiceCallState.SPEAKING -> Color(0xFF10B981)
                                        VoiceCallState.THINKING -> RoyalGold
                                        VoiceCallState.IDLE -> TextSecondary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "مكالمة رمسيس المباشرة" else "Ask Rameses Live",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    // Language Switcher Button (🇸🇦 عربي / 🇬🇧 EN)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x26FFFFFF))
                            .border(1.dp, RoyalGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { onToggleLanguage() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = RoyalGoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ARABIC) "🇪🇬 عربي" else "🇬🇧 EN",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Center Hologram Visualizer
                RoyalGeminiHologramVisualizer(
                    state = voiceCallState,
                    rmsDb = rmsDb
                )

                // Status Label & Live Subtitle Bubble
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusText = when (voiceCallState) {
                        VoiceCallState.LISTENING -> AppStrings.liveStatusListening(appLanguage)
                        VoiceCallState.THINKING -> AppStrings.liveStatusThinking(appLanguage)
                        VoiceCallState.SPEAKING -> AppStrings.liveStatusSpeaking(appLanguage)
                        VoiceCallState.IDLE -> AppStrings.liveStatusIdle(appLanguage)
                    }

                    Text(
                        text = statusText,
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = when (voiceCallState) {
                            VoiceCallState.LISTENING -> EgyptianCyan
                            VoiceCallState.SPEAKING -> RoyalGoldLight
                            VoiceCallState.THINKING -> RoyalGold
                            VoiceCallState.IDLE -> TextSecondary
                        },
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x260F172A)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (voiceCallState) {
                                VoiceCallState.SPEAKING -> RoyalGold.copy(alpha = 0.6f)
                                VoiceCallState.LISTENING -> EgyptianCyan.copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.12f)
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayText = if (voiceCallState == VoiceCallState.SPEAKING && lastRamsesSpeech.isNotBlank()) {
                                lastRamsesSpeech
                            } else if (lastTranscript.isNotBlank()) {
                                "« $lastTranscript »"
                            } else {
                                AppStrings.liveCallHint(appLanguage)
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

                // Quick Topic Suggestions Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSuggestions) { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x2A1E293B))
                                .border(1.dp, RoyalGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .clickable { onQuickPromptClick(suggestion) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = RoyalGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = suggestion,
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // Bottom Modern Gemini Live Call Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Speaker Toggle
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) onStopSpeaking()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0x33DC2626) else Color(0x22FFFFFF))
                            .border(1.dp, if (isMuted) Color(0xFFDC2626) else Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = AppStrings.muteSpeaker(appLanguage),
                            tint = if (isMuted) Color(0xFFF87171) else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Main Mic Action (Start/Stop listening or push to speak)
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
                                if (voiceCallState == VoiceCallState.LISTENING) EgyptianCyan else RoyalGold
                            )
                            .testTag("gemini_live_mic_action")
                    ) {
                        Icon(
                            imageVector = if (voiceCallState == VoiceCallState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Speak",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
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
                            contentDescription = AppStrings.endCall(appLanguage),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
