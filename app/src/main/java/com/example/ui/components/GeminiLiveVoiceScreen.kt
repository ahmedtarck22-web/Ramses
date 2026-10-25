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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.AvatarRigConfig
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

            val normalizedRms = (rmsDb.coerceIn(0f, 15f) / 15f)
            val baseHeight = when (state) {
                VoiceCallState.LISTENING -> (7f + normalizedRms * 28f * animProgress).dp
                VoiceCallState.SPEAKING -> (9f + (maxOf(0.35f, normalizedRms) * 28f * animProgress)).dp
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
    rigConfig: AvatarRigConfig = AvatarRigConfig(),
    isNativeLiveConnected: Boolean = false,
    isConnectingLive: Boolean = false,
    isMicMuted: Boolean = false,
    isSpeakerMuted: Boolean = false,
    onToggleMicMute: () -> Unit = {},
    onToggleSpeakerMute: () -> Unit = {},
    onToggleLanguage: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onEndCall: () -> Unit
) {
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
                            Color(0xFF040816),
                            Color(0xFF071126),
                            Color(0xFF0B1B3D),
                            Color(0xFF050B18)
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

                    // Real-Time Gemini Live WebSocket Status Pill
                    val mins = callSeconds / 60
                    val secs = callSeconds % 60
                    val timeLabel = String.format(Locale.US, "%02d:%02d", mins, secs)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isNativeLiveConnected) Color(0x2610B981)
                                else if (isConnectingLive) Color(0x26F59E0B)
                                else Color(0x22FFFFFF)
                            )
                            .border(
                                1.dp,
                                if (isNativeLiveConnected) Color(0xFF10B981).copy(alpha = 0.6f)
                                else if (isConnectingLive) RoyalGold.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isNativeLiveConnected) Color(0xFF10B981)
                                        else if (isConnectingLive) RoyalGold
                                        else EgyptianCyan
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isConnectingLive -> if (isArabic) "جاري فتح البث الصوتي المباشر..." else "Connecting Live Stream..."
                                    isNativeLiveConnected -> if (isArabic) "بث حي مباشر 24kHz • $timeLabel" else "Live Native Audio • $timeLabel"
                                    else -> timeLabel
                                },
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

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

                // 2. Centerpiece: King Ramses 3D Model shifted down gracefully
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.05f)
                        .padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        RamsesAvatar3DView(
                            modifier = Modifier.fillMaxSize(),
                            isSpeaking = voiceCallState == VoiceCallState.SPEAKING,
                            isListening = voiceCallState == VoiceCallState.LISTENING,
                            isThinking = voiceCallState == VoiceCallState.THINKING,
                            rmsDb = rmsDb,
                            appLanguage = appLanguage,
                            rigConfig = rigConfig,
                            onStopSpeaking = onStopSpeaking,
                            isAutoSpeakEnabled = true,
                            showBackgroundGradient = false,
                            verticalOffset = -0.10f
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Audio Frequency Bars
                    GeminiLiveEqualizerBars(
                        state = voiceCallState,
                        rmsDb = rmsDb
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Dedicated AI & Conversation Response Section directly below the avatar
                val responseScrollState = rememberScrollState()

                LaunchedEffect(lastRamsesSpeech.length) {
                    if (lastRamsesSpeech.isNotBlank() && !responseScrollState.isScrollInProgress && responseScrollState.maxValue > 0) {
                        responseScrollState.animateScrollTo(responseScrollState.maxValue)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp, max = 165.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x660B172E),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (voiceCallState == VoiceCallState.SPEAKING) RoyalGold.copy(alpha = 0.6f) else Color(0x33FFFFFF)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(responseScrollState)
                                .testTag("live_voice_response_scroll")
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (voiceCallState == VoiceCallState.SPEAKING) {
                                Text(
                                    text = stringResource(if (isArabic) R.string.live_echo_guard_ar else R.string.live_echo_guard_en),
                                    fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (lastTranscript.isNotBlank()) {
                                Text(
                                    text = "« $lastTranscript »",
                                    fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFF93C5FD),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }

                            when {
                                lastRamsesSpeech.isNotBlank() && (voiceCallState == VoiceCallState.SPEAKING || voiceCallState == VoiceCallState.LISTENING || voiceCallState == VoiceCallState.IDLE) -> {
                                    Text(
                                        text = lastRamsesSpeech,
                                        fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                        fontSize = 14.sp,
                                        color = RoyalGoldLight,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                }
                                voiceCallState == VoiceCallState.THINKING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = EgyptianCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = if (isConnectingLive) {
                                                if (isArabic) "جاري الاتصال بصوت Gemini Live المباشر..." else "Connecting to real Gemini Live audio..."
                                            } else {
                                                if (isArabic) "رمسيس يستجيب الآن..." else "Rameses is responding..."
                                            },
                                            fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                            fontSize = 13.sp,
                                            color = EgyptianCyan
                                        )
                                    }
                                }
                                voiceCallState == VoiceCallState.LISTENING -> {
                                    Text(
                                        text = if (isArabic) "« تحدّث بحرية في أي وقت.. رمسيس يسمعك مباشرة دون تداخل صدى السماعة »" else "« Speak freely anytime.. Rameses hears you clearly with Acoustic Echo Guard »",
                                        fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00E5FF).copy(alpha = 0.88f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {
                                    Text(
                                        text = if (isArabic) "الميكروفون مكتوم مؤقتاً — اضغط زر الميكروفون للمتابعة" else "Microphone muted — tap the mic button to resume",
                                        fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.55f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                // Quick voice prompt chips for instant interactive voice dialogue
                val quickPrompts = if (isArabic) listOf(
                    "👑 من أنت يا رمسيس؟",
                    "🏛️ كيف بنيت معبد أبو سمبل؟",
                    "⚔️ حدثني عن معركة قادش",
                    "☀️ ما هي حكمتك اليوم؟"
                ) else listOf(
                    "👑 Who are you, Pharaoh?",
                    "🏛️ Tell me about Abu Simbel",
                    "⚔️ The Battle of Kadesh",
                    "☀️ Your royal wisdom"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (prompt in quickPrompts) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x331E293B))
                                .border(1.dp, RoyalGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .clickable { onQuickPromptClick(prompt) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 12.sp,
                                fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                color = RoyalGoldLight,
                                fontWeight = FontWeight.Medium
                            )
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
                        onClick = { onToggleSpeakerMute() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerMuted) Color(0x33DC2626) else Color(0x22FFFFFF))
                            .border(1.dp, if (isSpeakerMuted) Color(0xFFDC2626) else Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute/Unmute Speaker",
                            tint = if (isSpeakerMuted) Color(0xFFF87171) else Color.White,
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

                    // Primary Live Mic Action (Mute/Unmute in Full-Duplex Live, or Interrupt AI Speech)
                    Box(contentAlignment = Alignment.Center) {
                        if (voiceCallState == VoiceCallState.LISTENING && !isMicMuted) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                            )
                        }

                        IconButton(
                            onClick = {
                                if (voiceCallState == VoiceCallState.SPEAKING) {
                                    // Instant barge-in interruption!
                                    onStopSpeaking()
                                } else if (isNativeLiveConnected) {
                                    onToggleMicMute()
                                } else if (voiceCallState == VoiceCallState.LISTENING) {
                                    onStopListening()
                                } else {
                                    onStartListening()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        voiceCallState == VoiceCallState.SPEAKING -> RoyalGoldLight
                                        isMicMuted -> Color(0xFF334155)
                                        voiceCallState == VoiceCallState.LISTENING -> Color(0xFF00E5FF)
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .border(
                                    2.dp,
                                    if (voiceCallState == VoiceCallState.LISTENING && !isMicMuted) Color(0xFFE0F7FA) else RoyalGold,
                                    CircleShape
                                )
                                .testTag("gemini_live_mic_action")
                        ) {
                            Icon(
                                imageVector = when {
                                    voiceCallState == VoiceCallState.SPEAKING -> Icons.Default.Stop
                                    isMicMuted -> Icons.Default.MicOff
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Mic Action",
                                tint = if ((voiceCallState == VoiceCallState.LISTENING && !isMicMuted) || voiceCallState == VoiceCallState.SPEAKING) Color.Black else Color.White,
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
