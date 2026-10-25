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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.model.AppLanguage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.service.GeminiLiveWebSocketClient
import com.example.ui.components.RagKnowledgeDialog
import com.example.ui.components.Ramses3DArtifactDialog
import com.example.ui.components.RamsesAvatar3DView
import com.example.ui.components.RamsesHeader
import com.example.ui.components.RamsesInputBar
import com.example.ui.components.RiggingStudioDialog
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
import com.example.firebase.configureAppCheck
import com.example.ui.viewmodel.RamsesViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: RamsesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
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
    val liveModelSpeechTranscript by viewModel.liveModelSpeechTranscript.collectAsStateWithLifecycle()
    val liveConnectionStatus by viewModel.liveConnectionStatus.collectAsStateWithLifecycle()
    val isLiveMicMuted by viewModel.isLiveMicMuted.collectAsStateWithLifecycle()
    val isLiveSpeakerMuted by viewModel.isLiveSpeakerMuted.collectAsStateWithLifecycle()
    val isVoiceTyping by viewModel.isVoiceTyping.collectAsStateWithLifecycle()
    val liveRmsDb by viewModel.combinedRmsDb.collectAsStateWithLifecycle()
    val streamingMessage by viewModel.streamingMessage.collectAsStateWithLifecycle()
    val is3DViewMode by viewModel.is3DViewMode.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isAutoSpeakEnabled by viewModel.isAutoSpeakEnabled.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedGem by viewModel.selectedGem.collectAsStateWithLifecycle()
    val avatarRigConfig by viewModel.avatarRigConfig.collectAsStateWithLifecycle()
    val fileSearchPipelineState by viewModel.fileSearchPipelineState.collectAsStateWithLifecycle()
    val linkedSearchInstance by viewModel.linkedSearchInstance.collectAsStateWithLifecycle()
    val searchInstanceConfig by viewModel.searchInstanceConfig.collectAsStateWithLifecycle()
    val cloudRagBooks by viewModel.cloudRagBooks.collectAsStateWithLifecycle()
    val cloudRagFolders by viewModel.cloudRagFolders.collectAsStateWithLifecycle()
    val cloudRagSyncState by viewModel.cloudRagSyncState.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRagDialog by remember { mutableStateOf(false) }
    var showRigStudioDialog by remember { mutableStateOf(false) }
    var show3DArtifactsDialog by remember { mutableStateOf(false) }
    var showModelSelectorDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var autoFollowStream by remember { mutableStateOf(true) }
    val canScrollDown by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // Track manual user scroll gestures so streaming never fights or blocks manual scrolling
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.canScrollForward
        }.collect { (scrolling, canForward) ->
            if (scrolling && canForward) {
                autoFollowStream = false
            } else if (!canForward) {
                autoFollowStream = true
            }
        }
    }

    // Scroll to bottom when a new message is sent/added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            autoFollowStream = true
            val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(messages.size - 1)
            listState.animateScrollToItem(lastIdx)
        }
    }

    // Follow streaming text smoothly only when user has not scrolled up
    LaunchedEffect(streamingMessage?.text?.length, isLoading) {
        if (autoFollowStream && !listState.isScrollInProgress) {
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            if (lastIdx >= 0) {
                listState.scrollToItem(lastIdx)
            }
        }
    }

    // Audio record permission launcher for Gemini Live call
    val geminiLivePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startGeminiLiveCall()
        } else {
            Toast.makeText(context, if (appLanguage == AppLanguage.ARABIC) "إذن الميكروفون مطلوب للتفاعل الصوتي" else "Microphone permission required for voice interaction", Toast.LENGTH_SHORT).show()
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
                    onOpenRigStudioDialog = { showRigStudioDialog = true },
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
                                    rigConfig = avatarRigConfig,
                                    onOpenRigStudio = { showRigStudioDialog = true },
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
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
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

                                            item(key = "chat_bottom_anchor_split") {
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }

                                // Floating Scroll-to-Bottom Button in Split View (inside BoxScope)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canScrollDown && (messages.isNotEmpty() || streamingMessage != null),
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 10.dp)
                                ) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            autoFollowStream = true
                                            coroutineScope.launch {
                                                val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                                listState.animateScrollToItem(lastIdx)
                                            }
                                        },
                                        shape = CircleShape,
                                        containerColor = RoyalGold,
                                        contentColor = PharaonicMidnight,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("scroll_to_bottom_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.scroll_to_bottom_desc)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // =========================================================================
                        // FULL CHAT VIEW MODE: Full-Height Chat Stream + 3D Mode Toggle Floating Pill
                        // =========================================================================
                        Column(modifier = Modifier.fillMaxSize()) {

                            if (messages.isEmpty() && streamingMessage == null) {
                                // Signature Google Gemini Home Welcome Screen (fully scrollable on all screens)
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .testTag("home_welcome_scroll")
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
                                                if (appLanguage == AppLanguage.ARABIC) "استجابة لحظية فائقة السرعة • Gemini 3.1 Flash Lite" else "Sub-Second Speed • Gemini 3.1 Flash Lite"
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

                                Box(modifier = Modifier.fillMaxSize()) {
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

                                        item(key = "chat_bottom_anchor_full") {
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                    }

                                    // Floating Scroll-to-Bottom Button in Full Chat Mode
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = canScrollDown && (messages.isNotEmpty() || streamingMessage != null),
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = 14.dp)
                                    ) {
                                        SmallFloatingActionButton(
                                            onClick = {
                                                autoFollowStream = true
                                                coroutineScope.launch {
                                                    val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                                    listState.animateScrollToItem(lastIdx)
                                                }
                                            },
                                            shape = CircleShape,
                                            containerColor = RoyalGold,
                                            contentColor = PharaonicMidnight,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("scroll_to_bottom_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.scroll_to_bottom_desc)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real Full-Duplex Gemini Live Voice Screen (BidiGenerateContent WebSocket)
            if (isGeminiLiveActive) {
                val liveSpeech = liveModelSpeechTranscript.takeIf { it.isNotBlank() }
                    ?: streamingMessage?.text?.takeIf { it.isNotBlank() }
                    ?: messages.lastOrNull { it.sender == com.example.data.model.MessageSender.RAMSES }?.text
                    ?: ""

                GeminiLiveVoiceScreen(
                    voiceCallState = voiceCallState,
                    lastTranscript = currentVoiceTranscript,
                    lastRamsesSpeech = liveSpeech,
                    rmsDb = liveRmsDb,
                    appLanguage = appLanguage,
                    rigConfig = avatarRigConfig,
                    isNativeLiveConnected = liveConnectionStatus == GeminiLiveWebSocketClient.LiveConnectionStatus.CONNECTED,
                    isConnectingLive = liveConnectionStatus == GeminiLiveWebSocketClient.LiveConnectionStatus.CONNECTING,
                    isMicMuted = isLiveMicMuted,
                    isSpeakerMuted = isLiveSpeakerMuted,
                    onToggleMicMute = { viewModel.toggleLiveMicMute() },
                    onToggleSpeakerMute = { viewModel.toggleLiveSpeakerMute() },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onStartListening = { viewModel.startListeningForGeminiLive() },
                    onStopListening = { viewModel.stopListeningForGeminiLive() },
                    onStopSpeaking = { viewModel.stopSpeaking() },
                    onQuickPromptClick = { prompt -> viewModel.sendLiveVoicePrompt(prompt) },
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
                    onDismiss = { showAuthDialog = false },
                    onGoogleSignInClick = {
                        val act = context as? android.app.Activity
                        if (act != null) {
                            viewModel.launchGoogleSignIn(act) { success, errorMsg ->
                                if (success) {
                                    showAuthDialog = false
                                    Toast.makeText(
                                        context,
                                        if (appLanguage == AppLanguage.ARABIC) "تم تسجيل الدخول بنجاح عبر Google" else "Successfully signed in with Google",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (errorMsg != null) {
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
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
                    onDismiss = { showRagDialog = false },
                    pipelineState = fileSearchPipelineState,
                    linkedInstance = linkedSearchInstance,
                    searchConfig = searchInstanceConfig,
                    onUploadPdfUris = { uris ->
                        viewModel.uploadPdfBooksToCloudAndLink(uris) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveSearchConfig = { updatedCfg ->
                        viewModel.updateSearchInstanceConfig(updatedCfg)
                    },
                    cloudRagBooks = cloudRagBooks,
                    cloudRagFolders = cloudRagFolders,
                    cloudRagSyncState = cloudRagSyncState,
                    isUserSignedIn = userProfile.isSignedIn,
                    onSignInClick = {
                        showRagDialog = false
                        showAuthDialog = true
                    },
                    onSyncFiveBooksClick = {
                        viewModel.syncFiveBooksToCloudFirebase()
                    },
                    onConvertPdfUrisToCloud = { uris ->
                        viewModel.convertAndUploadPdfsToCloudFirebase(uris) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onLoadFolderChunks = { folderId, callback ->
                        viewModel.loadChunksForFolderPreview(folderId, callback)
                    }
                )
            }

            // 3D Rigging Studio & Custom .GLB File Importer Dialog
            if (showRigStudioDialog) {
                RiggingStudioDialog(
                    initialConfig = avatarRigConfig,
                    appLanguage = appLanguage,
                    onSaveConfig = { updatedRig ->
                        viewModel.saveAvatarRigConfig(updatedRig)
                    },
                    onDismiss = { showRigStudioDialog = false }
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

            // Google Gemini Model Selector Dialog (3.1 Flash Lite vs 3.8 Flash)
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
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                        ) {
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

                            // Option 1: Gemini 3.1 Flash Lite (Sub-second ~0.5s TTFT)
                            val isFastFlash = selectedModel != "gemini-3.8-flash"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isFastFlash) Color(0x334285F4) else Color(0xFF282A2C))
                                    .border(1.dp, if (isFastFlash) Color(0xFF8AB4F8) else Color(0xFF3C4043), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectModel("gemini-3.1-flash-lite")
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
                                            text = "Gemini 3.1 Flash Lite",
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

                            // Option 2: Gemini 3.8 Flash
                            val is38Flash = selectedModel == "gemini-3.8-flash"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (is38Flash) Color(0x334285F4) else Color(0xFF282A2C))
                                    .border(1.dp, if (is38Flash) Color(0xFF8AB4F8) else Color(0xFF3C4043), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectModel("gemini-3.8-flash")
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
                                            text = "Gemini 3.8 Flash",
                                            fontFamily = CinzelFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (appLanguage == AppLanguage.ARABIC) "تفكير عميق • Firebase AI" else "Deep Reasoning • Firebase AI",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC) "الأفضل في التحليل التاريخي العميق، فهرسة كتب الـ RAG السحابية، والبحوث المفصلة." else "Best for deep Egyptology synthesis, Cloud Firebase RAG analysis, and detailed research.",
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
