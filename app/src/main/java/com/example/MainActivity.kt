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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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

    val isGeminiLiveActive by viewModel.isGeminiLiveActive.collectAsStateWithLifecycle()
    val voiceCallState by viewModel.voiceCallState.collectAsStateWithLifecycle()
    val currentVoiceTranscript by viewModel.currentVoiceTranscript.collectAsStateWithLifecycle()
    val isVoiceTyping by viewModel.isVoiceTyping.collectAsStateWithLifecycle()
    val liveRmsDb by viewModel.speechHelper.rmsDb.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRagDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Automatically scroll to bottom when messages update
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Audio record permission launcher for Gemini Live call
    val geminiLivePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startGeminiLiveCall()
        } else {
            Toast.makeText(context, "يلزم إذن الميكروفون للمكالمة الصوتية المباشرة (Gemini Live)", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio record permission launcher for voice typing
    val voiceTypingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceTyping()
        } else {
            Toast.makeText(context, "يلزم إذن الميكروفون للإملاء الصوتي", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "تم إرفاق الصورة للتحليل", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر قراءة الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenuDrawer(
                userProfile = userProfile,
                sessions = sessions,
                currentSessionId = currentSessionId,
                onSelectSession = { sid -> viewModel.switchSession(sid) },
                onDeleteSession = { sid -> viewModel.deleteSession(sid) },
                isSearchGroundingEnabled = isSearchGroundingEnabled,
                onToggleSearchGrounding = { viewModel.toggleSearchGrounding() },
                onOpenAuthDialog = { showAuthDialog = true },
                onOpenApiKeyDialog = { showApiKeyDialog = true },
                onOpenRagDialog = { showRagDialog = true },
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
                    // Upper header with Ramses icon, title Ask Ramses, and personal API key shortcut
                    RamsesHeader(
                        hasPersonalApiKey = customApiKey.isNotBlank(),
                        onProfileClick = { showAuthDialog = true },
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
                        isListeningVoiceTyping = isVoiceTyping
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (messages.isEmpty()) {
                        // Upper Part Center Emblem & 1.5M Token Status when session is empty
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Upper Ramses App Icon
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(24.dp))
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
                                        RoundedCornerShape(24.dp)
                                    )
                                    .padding(4.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ask_ramses_app_icon_1791815852062),
                                    contentDescription = "أيقونة اسأل رمسيس",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "ASK RAMSES",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )

                            Text(
                                text = "اسأل رمسيس",
                                fontFamily = AmiriFontFamily,
                                fontSize = 16.sp,
                                color = RoyalGoldLight
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // 1.5 Million Tokens Quota Card for each user
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
                                    .padding(16.dp)
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
                                            text = if (customApiKey.isNotBlank()) "مفتاحك الشخصي نشط • 1.5M Token" else "لكل مستخدم 1.5 مليون توكن مجاناً",
                                            fontFamily = AmiriFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (customApiKey.isNotBlank()) Color(0xFF10B981) else RoyalGoldLight
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (customApiKey.isNotBlank())
                                            "أنت تعمل بمفتاح Google AI Studio الخاص بك وبأقصى طاقة استيعابية."
                                        else
                                            "اضغط هنا لربط مفتاح Google AI Studio الشخصي والحصول على 1,500,000 توكن مجاناً لحسابك الخاص.",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatMessageBubble(
                                    message = message,
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

                            // Loading indicator item
                            if (isLoading) {
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
                                                    text = if (isSearchGroundingEnabled) "رمسيس يبحث ويوثق الحكمة..." else "رمسيس يفكر...",
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

            // Gemini Live Voice Screen (Triggered by Sparkle button)
            if (isGeminiLiveActive) {
                val lastRamsesMessage = messages.lastOrNull { it.sender == com.example.data.model.MessageSender.RAMSES }?.text ?: ""

                GeminiLiveVoiceScreen(
                    voiceCallState = voiceCallState,
                    lastTranscript = currentVoiceTranscript,
                    lastRamsesSpeech = lastRamsesMessage,
                    rmsDb = liveRmsDb,
                    onStartListening = { viewModel.startListeningForGeminiLive() },
                    onStopListening = { viewModel.stopListeningForGeminiLive() },
                    onStopSpeaking = { viewModel.stopSpeaking() },
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
                    onDismiss = { showApiKeyDialog = false }
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
        }
    }
}
