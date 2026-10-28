package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AvatarRigConfig
import com.example.data.model.VoiceCallState
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import kotlinx.coroutines.delay
import java.util.Locale

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
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animProgress by infiniteTransition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 340 + i * 105, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val normalizedRms = (rmsDb.coerceIn(0f, 15f) / 15f)
            val baseHeight = when (state) {
                VoiceCallState.LISTENING -> (7f + normalizedRms * 26f * animProgress).dp
                VoiceCallState.SPEAKING -> (10f + (maxOf(0.42f, normalizedRms) * 26f * animProgress)).dp
                VoiceCallState.THINKING -> (6f + (10f * animProgress)).dp
                VoiceCallState.IDLE -> 5.dp
            }

            Box(
                modifier = Modifier
                    .width(4.5.dp)
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
 * Classic Glowing Gemini Live Orb (restored for `selectedGem == "GEMINI_DEFAULT"`)
 * matching the signature deep-navy glowing blue/cyan 3-star sparkle experience.
 */
@Composable
private fun GeminiLiveSparkleOrb(
    voiceCallState: VoiceCallState,
    rmsDb: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val volumeScale = 1f + (rmsDb.coerceIn(0f, 15f) / 15f) * 0.10f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.testTag("gemini_live_orb_container")
    ) {
        Box(
            modifier = Modifier.size(236.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer concentric glow rings
            Box(
                modifier = Modifier
                    .size(228.dp)
                    .scale(pulse * volumeScale)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.14f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(208.dp)
                    .scale(pulse * 0.99f * volumeScale)
                    .clip(CircleShape)
                    .border(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.22f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(192.dp)
                    .scale(pulse * 0.98f * volumeScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0EA5E9).copy(alpha = 0.22f),
                                Color(0xFF1D4ED8).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.4.dp, Color(0xFF60A5FA).copy(alpha = 0.30f), CircleShape)
            )

            // Main Glowing Blue-to-Cyan Gemini Orb
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(if (voiceCallState == VoiceCallState.SPEAKING || voiceCallState == VoiceCallState.LISTENING) volumeScale else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2563EB),
                                Color(0xFF0284C7),
                                Color(0xFF22D3EE)
                            )
                        )
                    )
                    .border(2.5.dp, Color(0xFF7DD3FC).copy(alpha = 0.78f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemini Live",
                    tint = Color.White,
                    modifier = Modifier.size(74.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Gemini Live",
            fontFamily = CinzelFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 19.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Fullscreen Gemini Live Voice Screen:
 * - When `selectedGem == "GEMINI_DEFAULT"` (جيمناي): renders the classic deep-navy Gemini Live
 *   glowing blue/cyan sparkle orb UI.
 * - When `selectedGem != "GEMINI_DEFAULT"` (رمسيس): renders King Ramses II's interactive 3D .GLB
 *   model (`Ramses.final.glb`) on a pure `#000000` black background.
 */
@Composable
fun GeminiLiveVoiceScreen(
    voiceCallState: VoiceCallState,
    lastTranscript: String,
    lastRamsesSpeech: String,
    rmsDb: Float = 0f,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    rigConfig: AvatarRigConfig = AvatarRigConfig(),
    selectedGem: String = "RAMSES",
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    val isArabic = appLanguage == AppLanguage.ARABIC
    val isGeminiDefault = selectedGem == "GEMINI_DEFAULT"

    BackHandler(onBack = onEndCall)

    val rootBackgroundModifier = if (isGeminiDefault) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF040814),
                    Color(0xFF07132C),
                    Color(0xFF040711)
                )
            )
        )
    } else {
        Modifier.background(Color.Black)
    }

    val pillBgColor = if (isGeminiDefault) Color(0xFF131B2E) else Color(0xFF141414)
    val responseCardBgColor = if (isGeminiDefault) Color(0xFF080F1E) else Color(0xFF0A0A0A)
    val chipBgColor = if (isGeminiDefault) Color(0xFF0A1222) else Color(0xFF0E0E0E)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("gemini_live_overlay"),
        color = if (isGeminiDefault) Color(0xFF040814) else Color.Black,
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(rootBackgroundModifier)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Bar: Close Button, Timer/Status Pill & Language Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(pillBgColor)
                            .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .testTag("gemini_live_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Call",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val mins = callSeconds / 60
                    val secs = callSeconds % 60
                    val timeLabel = String.format(Locale.US, "%02d:%02d", mins, secs)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isNativeLiveConnected && !isGeminiDefault) Color(0xFF0A1F17)
                                else if (isConnectingLive) Color(0xFF1F1606)
                                else pillBgColor
                            )
                            .border(
                                1.dp,
                                if (isNativeLiveConnected && !isGeminiDefault) Color(0xFF10B981).copy(alpha = 0.6f)
                                else if (isConnectingLive) RoyalGold.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnectingLive) RoyalGold
                                        else EgyptianCyan
                                    )
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = when {
                                    isConnectingLive -> if (isArabic) "جاري الاتصال..." else "Connecting..."
                                    isNativeLiveConnected && !isGeminiDefault -> if (isArabic) "بث حي • $timeLabel" else "Live • $timeLabel"
                                    else -> timeLabel
                                },
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(pillBgColor)
                            .border(1.dp, RoyalGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .clickable { onToggleLanguage() }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = RoyalGoldLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isArabic) "🇪🇬 عربي" else "🇬🇧 EN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // 2. Centerpiece: Classic Glowing Orb for Gemini, or Real 3D .GLB Ramses II Model on Pure Black for Ramses
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.12f)
                        .padding(top = 6.dp)
                ) {
                    if (isGeminiDefault) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            GeminiLiveSparkleOrb(
                                voiceCallState = voiceCallState,
                                rmsDb = rmsDb
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black)
                                .testTag("gemini_live_3d_avatar_container"),
                            contentAlignment = Alignment.Center
                        ) {
                            RamsesAvatar3DView(
                                modifier = Modifier.fillMaxSize(),
                                isSpeaking = voiceCallState == VoiceCallState.SPEAKING,
                                isListening = voiceCallState == VoiceCallState.LISTENING && !isMicMuted,
                                isThinking = voiceCallState == VoiceCallState.THINKING || isConnectingLive,
                                rmsDb = rmsDb,
                                appLanguage = appLanguage,
                                rigConfig = rigConfig,
                                onStopSpeaking = onStopSpeaking,
                                showBackgroundGradient = false,
                                pureBlackBackground = true,
                                sceneSlot = "live",
                                verticalOffset = -0.08f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    GeminiLiveEqualizerBars(
                        state = voiceCallState,
                        rmsDb = rmsDb
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Dedicated AI & Conversation Response Section
                val responseScrollState = rememberScrollState()

                LaunchedEffect(lastRamsesSpeech.length) {
                    if (lastRamsesSpeech.isNotBlank() && !responseScrollState.isScrollInProgress && responseScrollState.maxValue > 0) {
                        responseScrollState.animateScrollTo(responseScrollState.maxValue)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp, max = 160.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = responseCardBgColor,
                    border = BorderStroke(
                        1.dp,
                        if (isGeminiDefault || voiceCallState == VoiceCallState.SPEAKING) {
                            RoyalGold.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.16f)
                        }
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
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
                                        fontSize = 15.sp,
                                        color = RoyalGoldLight,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 23.sp
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
                                                if (isArabic) "جاري الاستجابة الآن..." else "Responding now..."
                                            },
                                            fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                            fontSize = 13.sp,
                                            color = EgyptianCyan
                                        )
                                    }
                                }
                                voiceCallState == VoiceCallState.LISTENING -> {
                                    Text(
                                        text = if (isGeminiDefault) {
                                            if (isArabic) "أهلاً بك في Gemini Live! تفضّل بالتحدث، أنا أستمع إليك." else "Welcome to Gemini Live! Go ahead and speak, I'm listening."
                                        } else {
                                            if (isArabic) "« تحدّث بحرية الآن.. الصوت المباشر نشط »" else "« Speak freely now.. Live audio is active »"
                                        },
                                        fontFamily = if (isArabic) AmiriFontFamily else CinzelFontFamily,
                                        fontSize = 14.sp,
                                        color = if (isGeminiDefault) RoyalGoldLight else Color(0xFF00E5FF).copy(alpha = 0.88f),
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

                // 4. Expandable In-Call Text Input Bar
                AnimatedVisibility(
                    visible = showKeyboardInput,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(chipBgColor)
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
                                        text = if (isArabic) {
                                            if (isGeminiDefault) "اكتب سؤالك لـ Gemini هنا..." else "اكتب سؤالك لرمسيس هنا..."
                                        } else "Type your question here...",
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

                // 5. Quick voice prompt chips tailored to selected assistant
                val quickPrompts = if (isGeminiDefault) {
                    if (isArabic) listOf(
                        "✨ أهلاً Gemini، كيف حالك؟",
                        "💻 اشرح لي فكرة برمجية بسرعة",
                        "☀️ الجو عامل إيه والوقت كام؟",
                        "🎬 رشح لي فيلم كرتون رائع"
                    ) else listOf(
                        "✨ Hey Gemini, how are you?",
                        "💻 Explain a coding concept quickly",
                        "☀️ What's the weather and time?",
                        "🎬 Recommend a great animated movie"
                    )
                } else {
                    if (isArabic) listOf(
                        "👑 من أنت يا رمسيس؟",
                        "🏛️ كيف بنيت معبد أبو سمبل؟",
                        "☀️ الجو عامل إيه النهارده؟",
                        "⚔️ حدثني عن معركة قادش"
                    ) else listOf(
                        "👑 Who are you, Pharaoh?",
                        "🏛️ Tell me about Abu Simbel",
                        "☀️ How is the weather today?",
                        "⚔️ The Battle of Kadesh"
                    )
                }

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
                                .clip(RoundedCornerShape(18.dp))
                                .background(chipBgColor)
                                .border(1.dp, RoyalGold.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                                .clickable { onQuickPromptClick(prompt) }
                                .padding(horizontal = 13.dp, vertical = 7.dp)
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
                    IconButton(
                        onClick = { onToggleSpeakerMute() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerMuted) Color(0x33DC2626) else pillBgColor)
                            .border(1.dp, if (isSpeakerMuted) Color(0xFFDC2626) else Color.White.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSpeakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute/Unmute Speaker",
                            tint = if (isSpeakerMuted) Color(0xFFF87171) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showKeyboardInput = !showKeyboardInput },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (showKeyboardInput) Color(0xFF092226) else pillBgColor)
                            .border(1.dp, if (showKeyboardInput) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard Input",
                            tint = if (showKeyboardInput) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        if (voiceCallState == VoiceCallState.LISTENING && !isMicMuted) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.22f))
                            )
                        }

                        IconButton(
                            onClick = {
                                if (voiceCallState == VoiceCallState.SPEAKING) {
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
                                        isMicMuted -> Color(0xFF1F1F1F)
                                        voiceCallState == VoiceCallState.LISTENING -> Color(0xFF00E5FF)
                                        else -> pillBgColor
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
