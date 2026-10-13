package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.VoiceCallState
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceCallDialog(
    voiceCallState: VoiceCallState,
    lastTranscript: String,
    lastRamsesSpeech: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    Dialog(
        onDismissRequest = onEndCall,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PharaonicMidnight)
        ) {
            // Liquid Background Glow
            LiquidGlassBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = "مكالمة صوتية ملكية مباشرة",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = RoyalGoldLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "الملك رمسيس • استجابة فائقة السرعة",
                            fontFamily = AmiriFontFamily,
                            fontSize = 14.sp,
                            color = EgyptianCyan
                        )
                    }

                    // Center Avatar with Pulsing Aura
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(280.dp)
                    ) {
                        // Outer Pulsing Ring
                        if (voiceCallState == VoiceCallState.SPEAKING || voiceCallState == VoiceCallState.LISTENING) {
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                if (voiceCallState == VoiceCallState.SPEAKING) RoyalGold.copy(alpha = auraAlpha * 0.4f)
                                                else EgyptianCyan.copy(alpha = auraAlpha * 0.4f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        // Gold Border Ring
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 3.dp,
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            RoyalGold,
                                            EgyptianCyan,
                                            RoyalGoldLight,
                                            RoyalGold
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .padding(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ramses_portrait_1791810213480),
                                contentDescription = "الملك رمسيس الثاني في المكالمة",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Status Label and Subtitle Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = when (voiceCallState) {
                            VoiceCallState.LISTENING -> "الملك يستمع إليك... تفضل بالحديث"
                            VoiceCallState.THINKING -> "الملك يستحضر الحكمة السريعة..."
                            VoiceCallState.SPEAKING -> "الملك رمسيس يتحدث..."
                            VoiceCallState.IDLE -> "انقر على الميكروفون للتحدث مع الملك"
                        }

                        val statusColor = when (voiceCallState) {
                            VoiceCallState.LISTENING -> EgyptianCyan
                            VoiceCallState.THINKING -> RoyalGold
                            VoiceCallState.SPEAKING -> RoyalGoldLight
                            VoiceCallState.IDLE -> TextSecondary
                        }

                        Text(
                            text = statusText,
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = statusColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live transcript preview card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x26102A43)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val transcriptToShow = if (voiceCallState == VoiceCallState.SPEAKING && lastRamsesSpeech.isNotBlank()) {
                                    lastRamsesSpeech
                                } else if (lastTranscript.isNotBlank()) {
                                    "« $lastTranscript »"
                                } else {
                                    "تحدث بجملة أو سؤال وسيجيبك الملك رمسيس بحكمة مباشرة في 1-3 جمل."
                                }

                                Text(
                                    text = transcriptToShow,
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    // Bottom Call Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stop Speech / Mute button
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x26FFFFFF))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "إيقاف الصوت",
                                tint = TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Main Talk Button (Mic)
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
                                .testTag("voice_call_mic_button")
                        ) {
                            Icon(
                                imageVector = if (voiceCallState == VoiceCallState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "تحدث مع الملك",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // End Call Button
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "إنهاء المكالمة",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
