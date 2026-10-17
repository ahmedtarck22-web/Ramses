package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.model.AppLanguage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MessageSender
import com.example.ui.components.ApiKeyManagementDialog
import com.example.ui.components.AuthSignInDialog
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.GeminiLiveVoiceScreen
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.RagKnowledgeDialog
import com.example.ui.components.Ramses3DArtifactDialog
import com.example.ui.components.RamsesAvatar3DView
import com.example.ui.components.RamsesHeader
import com.example.ui.components.RamsesInputBar
import com.example.ui.components.SideMenuDrawer
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RamsesTheme
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.RamsesViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: RamsesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RamsesTheme {
                RamsesApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RamsesApp(viewModel: RamsesViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSearchGroundingEnabled by viewModel.isSearchGroundingEnabled.collectAsStateWithLifecycle()
    val selectedImageBitmap by viewModel.selectedImageBitmap.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val ragDocuments by viewModel.ragDocuments.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val isGeminiLiveActive by viewModel.isGeminiLiveActive.collectAsStateWithLifecycle()
    val voiceCallState by viewModel.voiceCallState.collectAsStateWithLifecycle()
    val currentVoiceTranscript by viewModel.currentVoiceTranscript.collectAsStateWithLifecycle()
    val isVoiceTyping by viewModel.isVoiceTyping.collectAsStateWithLifecycle()
    val liveRmsDb by viewModel.speechHelper.rmsDb.collectAsStateWithLifecycle()
    val streamingMessage by viewModel.streamingMessage.collectAsStateWithLifecycle()
    val is3DViewMode by viewModel.is3DViewMode.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isAutoSpeakEnabled by viewModel.isAutoSpeakEnabled.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRagDialog by remember { mutableStateOf(false) }
    var show3DArtifactsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Automatically scroll to bottom when messages or streaming text update
    LaunchedEffect(messages.size, streamingMessage?.text?.length, isLoading) {
        val totalCount = messages.size + (if (streamingMessage != null) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Audio record permission launcher for Gemini Live call
    val geminiLivePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startGeminiLiveCall()
        } else {
            Toast.makeText(context, "Audio permission required for Gemini Live call", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio record permission launcher for voice typing
    val voiceTypingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceTyping()
        } else {
            Toast.makeText(context, "Audio permission required for voice typing", Toast.LENGTH_SHORT).show()
        }
    }

    // Android Photo Picker launcher for multimodal analysis
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                viewModel.attachImage(bitmap)
                Toast.makeText(context, "Image attached for analysis", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (appLanguage == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SideMenuDrawer(
                    userProfile = userProfile,
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    appLanguage = appLanguage,
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onSelectSession = { sid -> viewModel.switchSession(sid) },
                    onDeleteSession = { sid -> viewModel.deleteSession(sid) },
                    onClearAllChats = { viewModel.clearAllConversations() },
                    isSearchGroundingEnabled = isSearchGroundingEnabled,
                    onToggleSearchGrounding = { viewModel.toggleSearchGrounding() },
                    onOpenAuthDialog = { showAuthDialog = true },
                    onOpenApiKeyDialog = { showApiKeyDialog = true },
                    onOpenRagDialog = { showRagDialog = true },
                    onOpen3DArtifactsDialog = { show3DArtifactsDialog = true },
                    onNewChatClick = { viewModel.startNewSession() },
                    onVoiceCallClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.startGeminiLiveCall()
                        } else {
                            geminiLivePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            LiquidGlassBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        // Upper header with Ramses icon, title Ask Rameses, language switch, voice call, 3D relics, and personal API key shortcut
                        RamsesHeader(
                            hasPersonalApiKey = customApiKey.isNotBlank(),
                            appLanguage = appLanguage,
                            onToggleLanguage = { viewModel.toggleLanguage() },
                            onProfileClick = { showAuthDialog = true },
                            onOpen3DArtifactsClick = { show3DArtifactsDialog = true },
                            is3DAvatarMode = is3DViewMode,
                            onToggle3DAvatarMode = { viewModel.toggle3DViewMode() },
                            onApiKeyClick = { showApiKeyDialog = true },
                            onMenuClick = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    },
                bottomBar = {
                    RamsesInputBar(
                        inputText = inputText,
                        onInputTextChanged = { inputText = it },
                        onSendMessage = { text ->
                            viewModel.sendMessage(text)
                            inputText = ""
                        },
                        onAttachClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onMicVoiceTypingClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                if (isVoiceTyping) {
                                    viewModel.stopVoiceTyping()
                                } else {
                                    viewModel.startVoiceTyping()
                                }
                            } else {
                                voiceTypingPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onGeminiLiveClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.startGeminiLiveCall()
                            } else {
                                geminiLivePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        selectedImageBitmap = selectedImageBitmap,
                        onClearAttachedImage = { viewModel.clearAttachedImage() },
                        isListeningVoiceTyping = isVoiceTyping,
                        appLanguage = appLanguage
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (is3DViewMode) {
                        // =========================================================================
                        // SPLIT VIEW MODE: Top 50% 3D Ramses Avatar, Bottom 50% Text Conversation
                        // =========================================================================
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 1. TOP HALF OF SCREEN (52% height): King Ramses II 3D Interactive Model
                            // Reacts in real time with body gestures, head nods, breathing, and phonetic lipsync!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.08f)
                            ) {
                                RamsesAvatar3DView(
                                    modifier = Modifier.fillMaxSize(),
                                    isSpeaking = isSpeaking,
                                    isListening = isVoiceTyping,
                                    isThinking = isLoading,
                                    rmsDb = liveRmsDb,
                                    appLanguage = appLanguage,
                                    onStopSpeaking = { viewModel.stopSpeaking() },
                                    onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                                    isAutoSpeakEnabled = isAutoSpeakEnabled
                                )
                            }

                            // 2. BOTTOM HALF OF SCREEN (48% height): Text conversation, recent speech, prompt suggestions
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.92f)
                                    .background(Color(0x59040711))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Compact Mode Switcher Bar at top of bottom half
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Switch to Full Chat View
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0x33102A43))
                                                .border(1.dp, RoyalGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                                .clickable { viewModel.toggle3DViewMode() }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = null,
                                                tint = RoyalGoldLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (appLanguage == AppLanguage.ARABIC) "عرض المحادثة كاملة" else "Full Chat View",
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RoyalGoldLight
                                            )
                                        }

                                        // Auto-Speech Status Toggle
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isAutoSpeakEnabled) Color(0x2210B981) else Color(0x22FFFFFF))
                                                .border(
                                                    1.dp,
                                                    if (isAutoSpeakEnabled) Color(0xFF10B981).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable { viewModel.toggleAutoSpeak() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isAutoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                                contentDescription = null,
                                                tint = if (isAutoSpeakEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (appLanguage == AppLanguage.ARABIC) {
                                                    if (isAutoSpeakEnabled) "النطق تلقائي" else "النطق صامت"
                                                } else {
                                                    if (isAutoSpeakEnabled) "Auto-Speak ON" else "Muted"
                                                },
                                                fontSize = 10.sp,
                                                color = if (isAutoSpeakEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.55f),
                                                fontFamily = AmiriFontFamily
                                            )
                                        }
                                    }

                                    // Conversation Area inside the bottom half
                                    if (messages.isEmpty() && streamingMessage == null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = if (appLanguage == AppLanguage.ARABIC) "تحدث أو اكتب لرمسيس لتشاهد حركته الملكية ونطق الشفاه:" else "Ask Rameses anything to see his royal gestures and speech:",
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            val quickPrompts = if (appLanguage == AppLanguage.ARABIC) {
                                                listOf(
                                                    "👑 من أنت يا رمسيس العظيم؟",
                                                    "🏛️ حدثني عن معبد أبو سمبل وتعامد الشمس",
                                                    "⚔️ ما سر معركة قادش وأول معاهدة سلام؟",
                                                    "📜 أعطني نصيحة ملكية لحياتي اليوم"
                                                )
                                            } else {
                                                listOf(
                                                    "👑 Who are you, O Great Rameses?",
                                                    "🏛️ Tell me about Abu Simbel temple",
                                                    "⚔️ What is the story of Kadesh?",
                                                    "📜 Share royal wisdom for my life"
                                                )
                                            }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                for (prompt in quickPrompts) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Color(0x331E293B))
                                                            .border(0.8.dp, RoyalGold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                            .clickable { viewModel.sendMessage(prompt) }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = prompt,
                                                            fontFamily = AmiriFontFamily,
                                                            fontSize = 12.sp,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val allDisplayedMessages = remember(messages, streamingMessage) {
                                            if (streamingMessage != null) messages + streamingMessage!! else messages
                                        }

                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(allDisplayedMessages, key = { it.id }) { message ->
                                                ChatMessageBubble(
                                                    message = message,
                                                    appLanguage = appLanguage,
                                                    onSpeakClick = { textToSpeak ->
                                                        viewModel.speakMessage(textToSpeak)
                                                    },
                                                    onOpenApiKeyClick = {
                                                        showApiKeyDialog = true
                                                    },
                                                    onRetryClick = {
                                                        val lastUserMessage = messages.findLast { it.sender == MessageSender.USER }
                                                        if (lastUserMessage != null) {
                                                            viewModel.sendMessage(lastUserMessage.text)
                                                        }
                                                    },
                                                    onDismissErrorClick = {
                                                        viewModel.deleteMessage(message.id)
                                                    }
                                                )
                                            }

                                            if (isLoading && streamingMessage == null) {
                                                item {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Start,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(16.dp))
                                                                .background(SurfaceGlass)
                                                                .border(1.dp, RoyalGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(16.dp),
                                                                    color = RoyalGold,
                                                                    strokeWidth = 2.dp
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = com.example.data.model.AppStrings.thinkingMessage(appLanguage, isSearchGroundingEnabled),
                                                                    fontFamily = AmiriFontFamily,
                                                                    fontSize = 13.sp,
                                                                    color = RoyalGold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // =========================================================================
                        // FULL CHAT VIEW MODE: Full-Height Chat Stream + 3D Mode Toggle Floating Pill
                        // =========================================================================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top floating bar to return to 3D Split View
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xD9060B14))
                                        .border(1.dp, RoyalGold, RoundedCornerShape(18.dp))
                                        .clickable { viewModel.toggle3DViewMode() }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewInAr,
                                        contentDescription = null,
                                        tint = RoyalGoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "تفعيل مجسم رمسيس 3D بالنصف العلوي" else "Enable 3D Ramses in Top Half",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalGoldLight
                                    )
                                }
                            }

                            if (messages.isEmpty()) {
                                // Upper Part Center Emblem & 1.5M Token Status when session is empty
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .border(
                                                2.dp,
                                                Brush.sweepGradient(
                                                    listOf(
                                                        RoyalGold,
                                                        EgyptianCyan,
                                                        RoyalGoldLight,
                                                        RoyalGold
                                                    )
                                                ),
                                                RoundedCornerShape(22.dp)
                                            )
                                            .padding(3.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ask_ramses_app_icon_1791815852062),
                                            contentDescription = "أيقونة اسأل رمسيس",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(18.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "ASK RAMESES",
                                        fontFamily = CinzelFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        letterSpacing = 1.sp,
                                        color = TextPrimary
                                    )

                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "اسأل رمسيس • الحكمة الملكية" else "Royal AI Wisdom",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 15.sp,
                                        color = RoyalGoldLight
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 1.5 Million Tokens Quota Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color(0x22102A43))
                                            .border(
                                                1.dp,
                                                if (customApiKey.isNotBlank()) Color(0xFF10B981).copy(alpha = 0.5f) else RoyalGold.copy(alpha = 0.35f),
                                                RoundedCornerShape(18.dp)
                                            )
                                            .clickable { showApiKeyDialog = true }
                                            .padding(14.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (customApiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = if (customApiKey.isNotBlank()) Color(0xFF10B981) else RoyalGold,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (customApiKey.isNotBlank()) {
                                                        if (appLanguage == AppLanguage.ARABIC) "مفتاحك الشخصي نشط • 1.5M Token" else "Personal Key Active • 1.5M Tokens"
                                                    } else {
                                                        if (appLanguage == AppLanguage.ARABIC) "لكل مستخدم 1.5 مليون توكن مجاناً" else "1.5 Million Free Tokens per User"
                                                    },
                                                    fontFamily = AmiriFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (customApiKey.isNotBlank()) Color(0xFF10B981) else RoyalGoldLight
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = if (customApiKey.isNotBlank()) {
                                                    if (appLanguage == AppLanguage.ARABIC)
                                                        "أنت تعمل بمفتاح Google AI Studio الخاص بك وبأقصى طاقة استيعابية."
                                                    else
                                                        "Operating with your personal Google AI Studio key at full capacity."
                                                } else {
                                                    if (appLanguage == AppLanguage.ARABIC)
                                                        "اضغط هنا لربط مفتاح Google AI Studio والحصول على 1,500,000 توكن مجاناً."
                                                    else
                                                        "Tap here to connect your personal Google AI Studio key for 1.5M free tokens."
                                                },
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                val allDisplayedMessages = remember(messages, streamingMessage) {
                                    if (streamingMessage != null) messages + streamingMessage!! else messages
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(allDisplayedMessages, key = { it.id }) { message ->
                                        ChatMessageBubble(
                                            message = message,
                                            appLanguage = appLanguage,
                                            onSpeakClick = { textToSpeak ->
                                                viewModel.speakMessage(textToSpeak)
                                            },
                                            onOpenApiKeyClick = {
                                                showApiKeyDialog = true
                                            },
                                            onRetryClick = {
                                                val lastUserMessage = messages.findLast { it.sender == MessageSender.USER }
                                                if (lastUserMessage != null) {
                                                    viewModel.sendMessage(lastUserMessage.text)
                                                }
                                            },
                                            onDismissErrorClick = {
                                                viewModel.deleteMessage(message.id)
                                            }
                                        )
                                    }

                                    if (isLoading && streamingMessage == null) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(SurfaceGlass)
                                                        .border(1.dp, RoyalGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            color = RoyalGold,
                                                            strokeWidth = 2.dp
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = com.example.data.model.AppStrings.thinkingMessage(appLanguage, isSearchGroundingEnabled),
                                                            fontFamily = AmiriFontFamily,
                                                            fontSize = 14.sp,
                                                            color = RoyalGold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Gemini Live Voice Screen (Triggered by Sparkle button)
            if (isGeminiLiveActive) {
                val lastRamsesMessage = messages.lastOrNull { it.sender == com.example.data.model.MessageSender.RAMSES }?.text ?: ""

                GeminiLiveVoiceScreen(
                    voiceCallState = voiceCallState,
                    lastTranscript = currentVoiceTranscript,
                    lastRamsesSpeech = lastRamsesMessage,
                    rmsDb = liveRmsDb,
                    appLanguage = appLanguage,
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onStartListening = { viewModel.startListeningForGeminiLive() },
                    onStopListening = { viewModel.stopListeningForGeminiLive() },
                    onStopSpeaking = { viewModel.stopSpeaking() },
                    onQuickPromptClick = { prompt -> viewModel.sendMessage(prompt, isVoice = true) },
                    onEndCall = { viewModel.endGeminiLiveCall() }
                )
            }

            // Google and Apple Sign-in Dialog
            if (showAuthDialog) {
                AuthSignInDialog(
                    userProfile = userProfile,
                    onSignInWithGoogle = { email, name ->
                        viewModel.signInWithGoogle(email, name)
                    },
                    onSignInWithApple = { email, name ->
                        viewModel.signInWithApple(email, name)
                    },
                    onSignOut = { viewModel.signOut() },
                    onDismiss = { showAuthDialog = false }
                )
            }

            // Dedicated API Key Management Dialog (1.5M Token Context per User)
            if (showApiKeyDialog) {
                ApiKeyManagementDialog(
                    currentApiKey = customApiKey,
                    onSaveApiKey = { newKey ->
                        viewModel.setCustomApiKey(newKey)
                    },
                    onDismiss = { showApiKeyDialog = false },
                    appLanguage = appLanguage
                )
            }

            // RAG Knowledge Dialog (Dedicated section to supply information/documents to Ramses)
            if (showRagDialog) {
                RagKnowledgeDialog(
                    documents = ragDocuments,
                    onAddDocument = { title, content, cat ->
                        viewModel.addRagDocument(title, content, cat)
                    },
                    onToggleDocument = { doc ->
                        viewModel.toggleRagDocument(doc)
                    },
                    onDeleteDocument = { id ->
                        viewModel.deleteRagDocument(id)
                    },
                    onDismiss = { showRagDialog = false }
                )
            }

            // 3D Ancient Egyptian Relics Gallery Dialog (SceneView)
            if (show3DArtifactsDialog) {
                Ramses3DArtifactDialog(
                    appLanguage = appLanguage,
                    onDismissRequest = { show3DArtifactsDialog = false },
                    onAskRamses = { prompt ->
                        viewModel.sendMessage(prompt)
                    }
                )
            }
        }
    }
}
}
