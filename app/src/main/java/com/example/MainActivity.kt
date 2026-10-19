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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedGem by viewModel.selectedGem.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRagDialog by remember { mutableStateOf(false) }
    var show3DArtifactsDialog by remember { mutableStateOf(false) }
    var showModelSelectorDialog by remember { mutableStateOf(false) }

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
                    selectedGem = selectedGem,
                    onSelectGem = { gemId -> viewModel.selectGem(gemId) },
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
                            selectedModelName = selectedModel,
                            onModelClick = { showModelSelectorDialog = true },
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
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

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

                            if (messages.isEmpty()) {
                                // Signature Google Gemini Home Welcome Screen (like Android / iOS app)
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Gemini Sparkle Icon with Gradient Glow
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        Color(0x334285F4),
                                                        Color(0x1A00E5FF),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .border(1.5.dp, Color(0xFF64B5F6).copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Gemini",
                                            tint = Color(0xFF8AB4F8),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Gradient Gemini Greeting
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "أهلاً بك، يا صديقي" else "Hello, friend",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "بم يمكنني مساعدتك اليوم؟" else "How can I help you today?",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 16.sp,
                                        color = Color(0xFF9AA0A6),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Signature Gemini Suggestion Prompt Cards (includes user's daily questions)
                                    val geminiPrompts = if (appLanguage == AppLanguage.ARABIC) {
                                        listOf(
                                            Pair("💬", "ازيك عامل إيه؟"),
                                            Pair("☀️", "الجو عامل إيه اليوم والطقس؟"),
                                            Pair("🏛️", "حدثني عن أسرار رمسيس وأبو سمبل وتاريخ مصر"),
                                            Pair("📝", "ساعدني في كتابة فكرة أو رسالة احترافية")
                                        )
                                    } else {
                                        listOf(
                                            Pair("💬", "How are you doing today?"),
                                            Pair("☀️", "What's the weather like today?"),
                                            Pair("🏛️", "Tell me the secrets of King Rameses & Abu Simbel"),
                                            Pair("📝", "Help me draft a creative idea or message")
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for ((emoji, promptText) in geminiPrompts) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0xFF1E1F20))
                                                    .border(1.dp, Color(0xFF333538), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        viewModel.sendMessage(promptText)
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = emoji,
                                                        fontSize = 18.sp,
                                                        modifier = Modifier.padding(end = 10.dp)
                                                    )
                                                    Text(
                                                        text = promptText,
                                                        fontFamily = AmiriFontFamily,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFFE8EAED)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // 1.5 Million Tokens Quota Status Pill
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0x1F4285F4))
                                            .border(0.8.dp, Color(0x404285F4), RoundedCornerShape(14.dp))
                                            .clickable { showApiKeyDialog = true }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = Color(0xFF8AB4F8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (customApiKey.isNotBlank()) {
                                                if (appLanguage == AppLanguage.ARABIC) "مفتاح AI Studio الشخصي نشط (1.5M Token)" else "Personal AI Studio Key Active (1.5M Tokens)"
                                            } else {
                                                if (appLanguage == AppLanguage.ARABIC) "استجابة فائقة السرعة بـ Gemini 3.8 Flash • 1.5M Token" else "Ultra-Fast with Gemini 3.8 Flash • 1.5M Tokens"
                                            },
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF8AB4F8)
                                        )
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
                val liveSpeech = streamingMessage?.text?.takeIf { it.isNotBlank() }
                    ?: messages.lastOrNull { it.sender == com.example.data.model.MessageSender.RAMSES }?.text
                    ?: ""

                GeminiLiveVoiceScreen(
                    voiceCallState = voiceCallState,
                    lastTranscript = currentVoiceTranscript,
                    lastRamsesSpeech = liveSpeech,
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

            // Google Gemini Model Selector Dialog (Flash 2.0 vs Pro 1.5)
            if (showModelSelectorDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showModelSelectorDialog = false }
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E1F20),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333538)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF8AB4F8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "اختيار نموذج Gemini" else "Select Gemini Model",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { showModelSelectorDialog = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Option 1: Gemini 2.0 Flash
                            val is20Flash = selectedModel.contains("2.0", ignoreCase = true) || !selectedModel.contains("pro", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (is20Flash) Color(0x334285F4) else Color(0xFF282A2C))
                                    .border(1.dp, if (is20Flash) Color(0xFF8AB4F8) else Color(0xFF3C4043), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectModel("gemini-2.0-flash")
                                        showModelSelectorDialog = false
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Gemini 2.0 Flash",
                                            fontFamily = CinzelFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (appLanguage == AppLanguage.ARABIC) "موصى به • فائق السرعة" else "Recommended • Ultra Fast",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF8AB4F8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "أسرع استجابة لحظية للمحادثات اليومية والطقس والمكالمات الصوتية المباشرة." else "Fastest sub-second responses for daily chats, weather, and live stream voice calls.",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Option 2: Gemini 1.5 Pro
                            val is15Pro = selectedModel.contains("pro", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (is15Pro) Color(0x334285F4) else Color(0xFF282A2C))
                                    .border(1.dp, if (is15Pro) Color(0xFF8AB4F8) else Color(0xFF3C4043), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectModel("gemini-1.5-pro")
                                        showModelSelectorDialog = false
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Gemini 1.5 Pro",
                                            fontFamily = CinzelFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (appLanguage == AppLanguage.ARABIC) "تفكير عميق" else "Deep Reasoning",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "الأفضل في تحليل الأكواد المعقدة، التفكير المنطقي المتسلسل، والبحوث الطويلة." else "Best for complex code analysis, multi-step logic, and detailed research.",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 18.sp
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
