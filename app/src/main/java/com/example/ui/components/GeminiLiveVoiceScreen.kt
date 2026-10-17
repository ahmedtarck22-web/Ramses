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
 * Authentic Gemini Live Radiant Plasma Orb
 * Fluid reactive sound sphere that ripples dynamically to voice amplitude and state transitions.
 */
@Composable
fun GeminiLiveRadiantOrb(
    state: VoiceCallState,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_orb")

    // Continuous rotation for plasma vortex
    val swirlAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swirl_angle"
    )

    // Breathing pulse
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Acoustic wave oscillation
    val acousticPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "acoustic_wave"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(260.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val audioVolumeBoost = if (state == VoiceCallState.LISTENING) {
                (rmsDb.coerceIn(0f, 16f) / 16f) * 45f
            } else if (state == VoiceCallState.SPEAKING) {
                28f
            } else {
                8f
            }

            val baseRadius = size.width * 0.30f * (if (state == VoiceCallState.SPEAKING) breathing * 1.08f else breathing)

            // 1. Outer Cosmic Nebula Glow
            val nebulaColors = when (state) {
                VoiceCallState.LISTENING -> listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.40f),
                    Color(0xFF0284C7).copy(alpha = 0.20f),
                    Color.Transparent
                )
                VoiceCallState.SPEAKING -> listOf(
                    Color(0xFFFFDF73).copy(alpha = 0.45f),
                    Color(0xFFD4AF37).copy(alpha = 0.25f),
                    Color.Transparent
                )
                VoiceCallState.THINKING -> listOf(
                    Color(0xFF818CF8).copy(alpha = 0.40f),
                    Color(0xFF6366F1).copy(alpha = 0.20f),
                    Color.Transparent
                )
                VoiceCallState.IDLE -> listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.20f),
                    Color.Transparent
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = nebulaColors,
                    center = center,
                    radius = baseRadius * 1.8f + audioVolumeBoost
                ),
                radius = baseRadius * 1.8f + audioVolumeBoost,
                center = center
            )

            // 2. Dynamic Expanding Soundwave Rings (when speaking or listening)
            if (state == VoiceCallState.LISTENING || state == VoiceCallState.SPEAKING) {
                val ringColor = if (state == VoiceCallState.LISTENING) {
                    Color(0xFF00E5FF).copy(alpha = 0.45f)
                } else {
                    Color(0xFFFFDF73).copy(alpha = 0.50f)
                }

                drawCircle(
                    color = ringColor,
                    radius = baseRadius * 1.40f + audioVolumeBoost * 0.6f,
                    center = center,
                    style = Stroke(width = 2.5f)
                )

                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = baseRadius * 1.62f + audioVolumeBoost * 0.8f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // 3. Fluid Living Gemini Plasma Orb (Multi-node Bézier mesh)
            val points = 16
            val path = Path()
            val angleStep = (2f * Math.PI.toFloat()) / points

            for (i in 0 until points) {
                val currentAngle = i * angleStep + Math.toRadians(swirlAngle.toDouble()).toFloat()
                val waveOffset = sin(currentAngle * 3f + acousticPhase) * (12f + audioVolumeBoost)
                val r = baseRadius + waveOffset

                val x = center.x + r * cos(currentAngle)
                val y = center.y + r * sin(currentAngle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevAngle = (i - 1) * angleStep + Math.toRadians(swirlAngle.toDouble()).toFloat()
                    val prevR = baseRadius + sin(prevAngle * 3f + acousticPhase) * (12f + audioVolumeBoost)
                    val prevX = center.x + prevR * cos(prevAngle)
                    val prevY = center.y + prevR * sin(prevAngle)

                    val cx = (prevX + x) / 2f
                    val cy = (prevY + y) / 2f
                    path.quadraticTo(prevX, prevY, cx, cy)
                }
            }
            path.close()

            val plasmaColors = when (state) {
                VoiceCallState.LISTENING -> listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF38BDF8),
                    Color(0xFF1D4ED8),
                    Color(0xFF00F0FF)
                )
                VoiceCallState.SPEAKING -> listOf(
                    Color(0xFFFFDF73),
                    Color(0xFFD4AF37),
                    Color(0xFFF59E0B),
                    Color(0xFFFFED8A)
                )
                VoiceCallState.THINKING -> listOf(
                    Color(0xFF818CF8),
                    Color(0xFFC084FC),
                    Color(0xFF6366F1),
                    Color(0xFFE5B842)
                )
                VoiceCallState.IDLE -> listOf(
                    Color(0xFF1E293B),
                    Color(0xFF334155),
                    Color(0xFF475569)
                )
            }

            val plasmaBrush = Brush.sweepGradient(
                colors = plasmaColors,
                center = center
            )

            drawPath(path = path, brush = plasmaBrush)

            // 4. Radiant Center Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        when (state) {
                            VoiceCallState.SPEAKING -> RoyalGoldLight.copy(alpha = 0.6f)
                            VoiceCallState.LISTENING -> Color(0xFF00E5FF).copy(alpha = 0.6f)
                            else -> Color(0xFF818CF8).copy(alpha = 0.5f)
                        },
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.50f
                ),
                radius = baseRadius * 0.50f,
                center = center
            )
        }

        // Center Royal Emblem
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color(0xD9070A13))
                .border(2.dp, if (state == VoiceCallState.SPEAKING) RoyalGold else EgyptianCyan, CircleShape)
        ) {
            Text(
                text = "𓇳", // Ra Sun Disc Hieroglyph
                fontSize = 28.sp,
                color = if (state == VoiceCallState.SPEAKING) RoyalGoldLight else Color(0xFFE0F7FA)
            )
        }
    }
}

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
    var is3DSceneViewMode by remember { mutableStateOf(false) }

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

                // 2. Centerpiece: Gemini Live Radiant Orb (or 3D SceneView Statue Toggle)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (is3DSceneViewMode) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(Color(0xD9060B14))
                                .border(2.dp, RoyalGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            RamsesSceneView(
                                modifier = Modifier.fillMaxSize(),
                                modelAssetPath = "models/ramses_glb.glb",
                                showControls = false,
                                initialAutoRotate = false,
                                isResponding = voiceCallState == VoiceCallState.SPEAKING,
                                idleAnimationEnabled = true,
                                rmsDb = rmsDb
                            )
                        }
                    } else {
                        GeminiLiveRadiantOrb(
                            state = voiceCallState,
                            rmsDb = rmsDb
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio Frequency Bars
                    GeminiLiveEqualizerBars(
                        state = voiceCallState,
                        rmsDb = rmsDb
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Switcher: 3D SceneView <-> Radiant Orb
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x24102A43))
                            .border(0.8.dp, RoyalGold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { is3DSceneViewMode = !is3DSceneViewMode }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (is3DSceneViewMode) Icons.Default.AutoAwesome else Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = RoyalGoldLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (is3DSceneViewMode) {
                                    if (isArabic) "العودة للكرة الضوئية" else "Switch to Radiant Orb"
                                } else {
                                    if (isArabic) "عرض مجسم رمسيس 3D (SceneView)" else "3D Ramses Model (SceneView)"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = RoyalGoldLight
                            )
                        }
                    }
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
