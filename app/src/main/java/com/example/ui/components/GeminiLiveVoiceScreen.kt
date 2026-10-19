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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Gemini Audio Frequency Equalizer Bar
 */
@Composable
fun GeminiLiveEqualizerBars(
    state: VoiceCallState,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 7
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_bars")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animProgress by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + i * 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val baseHeight = when (state) {
                VoiceCallState.LISTENING -> (8f + (rmsDb.coerceIn(0f, 14f) / 14f) * 28f * animProgress).dp
                VoiceCallState.SPEAKING -> (12f + (22f * animProgress)).dp
                VoiceCallState.THINKING -> (6f + (10f * animProgress)).dp
                VoiceCallState.IDLE -> 5.dp
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(baseHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (state) {
                            VoiceCallState.LISTENING -> Color(0xFF00E5FF)
                            VoiceCallState.SPEAKING -> RoyalGoldLight
                            VoiceCallState.THINKING -> Color(0xFF818CF8)
                            VoiceCallState.IDLE -> Color(0xFF475569)
                        }
                    )
            )
        }
    }
}

/**
 * Fullscreen Gemini Live Voice Screen
 * Matches Google Gemini Live's natural, continuous, low-latency hands-free experience.
 */
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
    var inCallTextInput by remember { mutableStateOf("") }
    var showKeyboardInput by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableStateOf(0) }

    // Live call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    val isArabic = appLanguage == AppLanguage.ARABIC

    val quickSuggestions = remember(appLanguage) {
        if (isArabic) {
            listOf(
                "حدثني عن معبد أبو سمبل",
                "أعطني حكمة ملكية لليوم",
                "كيف أنظم وقتي وأهدافي؟",
                "ما سر معركة قادش؟",
                "حدثني عن أعظم إنجازاتك"
            )
        } else {
            listOf(
                "Tell me about Abu Simbel",
                "Give me royal wisdom for today",
                "How to master discipline and focus?",
                "What is the story of Kadesh?",
                "Share your greatest imperial legacy"
            )
        }
    }

    Dialog(
        onDismissRequest = onEndCall,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF040711),
                            Color(0xFF080F22),
                            Color(0xFF03050C)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Bar: Close Button, Gemini Live Status Badge & Language Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minimalist Close Button
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("gemini_live_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Call",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center is intentionally empty for a pristine, immersive Gemini Live look
                    Spacer(modifier = Modifier.weight(1f))

                    // Language Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22FFFFFF))
                            .border(1.dp, RoyalGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { onToggleLanguage() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = RoyalGoldLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isArabic) "🇪🇬 عربي" else "🇬🇧 EN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // 2. Centerpiece: King Ramses 3D Model with real-time lip-sync and hand-to-chin gestures (Replaces Orb)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF040816),
                                        Color(0xFF08122A),
                                        Color(0xFF050B18)
                                    )
                                )
                            )
                            .border(1.5.dp, RoyalGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RamsesAvatar3DView(
                            modifier = Modifier.fillMaxSize(),
                            isSpeaking = voiceCallState == VoiceCallState.SPEAKING,
                            isListening = voiceCallState == VoiceCallState.LISTENING,
                            isThinking = voiceCallState == VoiceCallState.THINKING,
                            rmsDb = rmsDb,
                            appLanguage = appLanguage,
                            onStopSpeaking = onStopSpeaking,
                            isAutoSpeakEnabled = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Audio Frequency Bars
                    GeminiLiveEqualizerBars(
                        state = voiceCallState,
                        rmsDb = rmsDb
                    )
                }

                // 3. Live Subtitle & Transcript Card (Seamless Speech Flow)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusText = when (voiceCallState) {
                        VoiceCallState.LISTENING -> if (isArabic) "يستمع لصوتك الآن..." else "Listening to your voice..."
                        VoiceCallState.THINKING -> if (isArabic) "رمسيس يستحضر الإجابة..." else "Rameses is reasoning..."
                        VoiceCallState.SPEAKING -> if (isArabic) "رمسيس يتحدث..." else "Rameses is speaking..."
                        VoiceCallState.IDLE -> if (isArabic) "جاهز للاستماع" else "Ready to listen"
                    }

                    Text(
                        text = statusText,
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when (voiceCallState) {
                            VoiceCallState.LISTENING -> Color(0xFF00E5FF)
                            VoiceCallState.SPEAKING -> RoyalGoldLight
                            VoiceCallState.THINKING -> Color(0xFF818CF8)
                            VoiceCallState.IDLE -> TextSecondary
                        },
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x330B172E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (voiceCallState) {
                                VoiceCallState.SPEAKING -> RoyalGold.copy(alpha = 0.5f)
                                VoiceCallState.LISTENING -> Color(0xFF00E5FF).copy(alpha = 0.4f)
                                else -> Color.White.copy(alpha = 0.10f)
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .clickable {
                                    // Tap to interrupt speech if Ramses is currently speaking
                                    if (voiceCallState == VoiceCallState.SPEAKING) {
                                        onStopSpeaking()
                                        onStartListening()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val displayText = if (voiceCallState == VoiceCallState.SPEAKING && lastRamsesSpeech.isNotBlank()) {
                                lastRamsesSpeech
                            } else if (lastTranscript.isNotBlank()) {
                                "« $lastTranscript »"
                            } else {
                                if (isArabic) "تحدث بصوتك مباشرة وسيجيبك رمسيس فوراً..." else "Speak naturally and Rameses will reply instantly..."
                            }

                            Text(
                                text = displayText,
                                fontFamily = AmiriFontFamily,
                                fontSize = 14.sp,
                                color = if (voiceCallState == VoiceCallState.SPEAKING) RoyalGoldLight else Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // 4. Quick Suggestions Chips (One-Tap Prompts)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSuggestions) { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x2E1E293B))
                                .border(0.8.dp, RoyalGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .clickable { onQuickPromptClick(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = RoyalGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = suggestion,
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 5. Expandable In-Call Text Input Bar (if user chooses keyboard)
                AnimatedVisibility(
                    visible = showKeyboardInput,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x381E293B))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (inCallTextInput.isEmpty()) {
                                    Text(
                                        text = if (isArabic) "اكتب سؤالك لرمسيس هنا..." else "Type your question here...",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                                BasicTextField(
                                    value = inCallTextInput,
                                    onValueChange = { inCallTextInput = it },
                                    textStyle = TextStyle(
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    ),
                                    cursorBrush = SolidColor(RoyalGold),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (inCallTextInput.isNotBlank()) {
                                                val text = inCallTextInput
                                                inCallTextInput = ""
                                                showKeyboardInput = false
                                                onQuickPromptClick(text)
                                            }
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (inCallTextInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val text = inCallTextInput
                                        inCallTextInput = ""
                                        showKeyboardInput = false
                                        onQuickPromptClick(text)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = RoyalGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Gemini Live Bottom Control Dock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0x33DC2626) else Color(0x22FFFFFF))
                            .border(1.dp, if (isMuted) Color(0xFFDC2626) else Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute/Unmute Speaker",
                            tint = if (isMuted) Color(0xFFF87171) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Keyboard input toggle
                    IconButton(
                        onClick = { showKeyboardInput = !showKeyboardInput },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (showKeyboardInput) Color(0x3300E5FF) else Color(0x22FFFFFF))
                            .border(1.dp, if (showKeyboardInput) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard Input",
                            tint = if (showKeyboardInput) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Primary Live Mic Action
                    Box(contentAlignment = Alignment.Center) {
                        if (voiceCallState == VoiceCallState.LISTENING) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                            )
                        }

                        IconButton(
                            onClick = {
                                if (voiceCallState == VoiceCallState.LISTENING) {
                                    onStopListening()
                                } else if (voiceCallState == VoiceCallState.SPEAKING) {
                                    // Interrupt and start listening
                                    onStopSpeaking()
                                    onStartListening()
                                } else {
                                    onStartListening()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    when (voiceCallState) {
                                        VoiceCallState.LISTENING -> Color(0xFF00E5FF)
                                        VoiceCallState.SPEAKING -> RoyalGoldLight
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .border(
                                    2.dp,
                                    if (voiceCallState == VoiceCallState.LISTENING) Color(0xFFE0F7FA) else RoyalGold,
                                    CircleShape
                                )
                                .testTag("gemini_live_mic_action")
                        ) {
                            Icon(
                                imageVector = when (voiceCallState) {
                                    VoiceCallState.LISTENING -> Icons.Default.Mic
                                    VoiceCallState.SPEAKING -> Icons.Default.Stop
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Mic Action",
                                tint = if (voiceCallState == VoiceCallState.LISTENING || voiceCallState == VoiceCallState.SPEAKING) Color.Black else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // End Call Button (Red Pill/Circle)
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .border(1.dp, Color(0xFFEF4444), CircleShape)
                            .testTag("gemini_live_end_call")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Live Call",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
