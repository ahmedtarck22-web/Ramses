package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.ChatSession
import com.example.data.model.UserProfile
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RoyalGold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = AmiriFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SideMenuDrawer(
    userProfile: UserProfile,
    hasPersonalApiKey: Boolean = false,
    sessions: List<ChatSession>,
    currentSessionId: String,
    selectedGem: String = "GEMINI_DEFAULT",
    onSelectGem: (String) -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    onToggleLanguage: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAllChats: () -> Unit,
    isSearchGroundingEnabled: Boolean,
    onToggleSearchGrounding: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onOpenRagDialog: () -> Unit,
    onOpen3DArtifactsDialog: () -> Unit,
    onNewChatClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val groupedHistory = remember(sessions, appLanguage) {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val todayHeader = if (appLanguage == AppLanguage.ARABIC) "اليوم" else "Today"
        val yesterdayHeader = if (appLanguage == AppLanguage.ARABIC) "أمس" else "Yesterday"
        val last7DaysHeader = if (appLanguage == AppLanguage.ARABIC) "آخر 7 أيام" else "Previous 7 Days"
        val olderHeader = if (appLanguage == AppLanguage.ARABIC) "المحادثات السابقة" else "Older"

        val map = linkedMapOf<String, MutableList<ChatSession>>()
        map[todayHeader] = mutableListOf()
        map[yesterdayHeader] = mutableListOf()
        map[last7DaysHeader] = mutableListOf()
        map[olderHeader] = mutableListOf()

        for (session in sessions.distinctBy { it.id }) {
            val diff = now - session.updatedAt
            when {
                diff < oneDay -> map[todayHeader]?.add(session)
                diff < 2 * oneDay -> map[yesterdayHeader]?.add(session)
                diff < 7 * oneDay -> map[last7DaysHeader]?.add(session)
                else -> map[olderHeader]?.add(session)
            }
        }
        map.filterValues { it.isNotEmpty() }
    }

    val drawerListState = rememberLazyListState()

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF131314),
        modifier = Modifier.width(320.dp)
    ) {
        // Unified full-height scrollable LazyColumn so swiping down/up works everywhere inside the drawer
        LazyColumn(
            state = drawerListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("side_menu_drawer_scroll"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. Top Bar: Gemini Sparkle Logo & Close
            item(key = "drawer_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini",
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "اسأل رمسيس • Gemini" else "Ask Rameses • Gemini",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("drawer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Prominent "+ New chat" Button
            item(key = "drawer_new_chat") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1F20))
                        .border(1.dp, Color(0xFF333538), RoundedCornerShape(24.dp))
                        .clickable {
                            onCloseDrawer()
                            onNewChatClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("drawer_new_chat_button"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = null,
                            tint = Color(0xFF8AB4F8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "محادثة جديدة" else "New chat",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2.5 Gems Section (المساعدين المخصصين)
            item(key = "drawer_gems_section") {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "المساعدين (Gems)" else "Gems",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF9AA0A6),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isGeminiSelected = selectedGem == "GEMINI_DEFAULT"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isGeminiSelected) Color(0x334285F4) else Color(0xFF1E1F20))
                            .border(
                                1.dp,
                                if (isGeminiSelected) Color(0xFF8AB4F8) else Color(0xFF333538),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onCloseDrawer()
                                onSelectGem("GEMINI_DEFAULT")
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("drawer_gem_gemini"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✨ Gemini",
                            fontFamily = AmiriFontFamily,
                            fontSize = 13.sp,
                            fontWeight = if (isGeminiSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isGeminiSelected) Color(0xFF8AB4F8) else Color.White
                        )
                    }

                    val isRamsesSelected = selectedGem == "RAMSES"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isRamsesSelected) Color(0x33F59E0B) else Color(0xFF1E1F20))
                            .border(
                                1.dp,
                                if (isRamsesSelected) RoyalGold else Color(0xFF333538),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onCloseDrawer()
                                onSelectGem("RAMSES")
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("drawer_gem_ramses"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "👑 رمسيس" else "👑 Ramses",
                            fontFamily = AmiriFontFamily,
                            fontSize = 13.sp,
                            fontWeight = if (isRamsesSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isRamsesSelected) RoyalGoldLight else Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 3. Quick Settings & Features (easily reachable & fully scrollable)
            item(key = "drawer_features_header") {
                HorizontalDivider(color = Color(0xFF333538))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "الأدوات والإعدادات الملكية" else "Royal Tools & Settings",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF9AA0A6),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item(key = "drawer_item_voice_call") {
                DrawerMenuItem(
                    icon = Icons.Default.Phone,
                    title = if (appLanguage == AppLanguage.ARABIC) "مكالمة صوتية مباشرة (Gemini Live)" else "Gemini Live Voice Call",
                    subtitle = if (appLanguage == AppLanguage.ARABIC) "تحدث بصوتك مباشرة في حوار تفاعلي حي" else "Direct real-time spoken dialogue",
                    onClick = {
                        onCloseDrawer()
                        onVoiceCallClick()
                    },
                    modifier = Modifier.testTag("drawer_voice_call_item")
                )
            }

            item(key = "drawer_item_rag") {
                DrawerMenuItem(
                    icon = Icons.Default.Description,
                    title = if (appLanguage == AppLanguage.ARABIC) "قاعدة المعرفة ورفع الملفات (RAG)" else "Knowledge Base & File Upload",
                    subtitle = if (appLanguage == AppLanguage.ARABIC) "إضافة كتب PDF وملفات نصية مرجعية" else "Upload custom PDF books & text files",
                    onClick = {
                        onCloseDrawer()
                        onOpenRagDialog()
                    },
                    modifier = Modifier.testTag("drawer_rag_files_item")
                )
            }

            item(key = "drawer_item_artifacts") {
                DrawerMenuItem(
                    icon = Icons.Default.ViewInAr,
                    title = if (appLanguage == AppLanguage.ARABIC) "معرض الآثار والمجسمات 3D" else "3D Artifacts & Models",
                    subtitle = if (appLanguage == AppLanguage.ARABIC) "تماثيل فرعونية تفاعلية بـ SceneView" else "Interactive 3D Egyptian relics",
                    onClick = {
                        onCloseDrawer()
                        onOpen3DArtifactsDialog()
                    },
                    modifier = Modifier.testTag("drawer_artifacts_item")
                )
            }

            item(key = "drawer_item_search_grounding") {
                DrawerMenuItem(
                    icon = Icons.Default.AutoAwesome,
                    title = if (appLanguage == AppLanguage.ARABIC) "بحث جوجل المباشر (Search Grounding)" else "Google Live Search",
                    subtitle = if (isSearchGroundingEnabled) AppStrings.searchGroundingEnabled(appLanguage) else AppStrings.searchGroundingDisabled(appLanguage),
                    onClick = onToggleSearchGrounding,
                    trailing = {
                        Switch(
                            checked = isSearchGroundingEnabled,
                            onCheckedChange = { onToggleSearchGrounding() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8AB4F8),
                                checkedTrackColor = Color(0xFF004A77)
                            )
                        )
                    }
                )
            }

            item(key = "drawer_item_api_key") {
                DrawerMenuItem(
                    icon = Icons.Default.Key,
                    title = if (appLanguage == AppLanguage.ARABIC) "مفتاح API الشخصي" else "Personal API Key",
                    subtitle = if (appLanguage == AppLanguage.ARABIC) "ربط مفتاح Google AI Studio والصورة الشخصية" else "Connect Google AI Studio key & profile",
                    onClick = {
                        onCloseDrawer()
                        onOpenApiKeyDialog()
                    }
                )
            }

            item(key = "drawer_item_language") {
                DrawerMenuItem(
                    icon = Icons.Default.Language,
                    title = if (appLanguage == AppLanguage.ARABIC) "لغة التطبيق (Language)" else "App Language",
                    subtitle = if (appLanguage == AppLanguage.ARABIC) "العربية 🇪🇬 (انقر للتبديل إلى English)" else "English 🇬🇧 (Tap to switch to العربية)",
                    onClick = { onToggleLanguage() },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x334285F4))
                                .border(1.dp, Color(0xFF8AB4F8), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.ARABIC) "عربي 🇪🇬" else "EN 🇬🇧",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF8AB4F8)
                            )
                        }
                    }
                )
            }

            // 4. Partitioned Recent Chats (سجل المحادثات المقسم)
            item(key = "drawer_recent_chats_header") {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color(0xFF333538))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.ARABIC) "المحادثات الأخيرة" else "Recent Chats",
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF9AA0A6),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    if (sessions.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAllChats,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = AppStrings.clearAllChats(appLanguage),
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (groupedHistory.isEmpty()) {
                item(key = "drawer_empty_history") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "لا توجد محادثات سابقة بعد" else "No recent chats yet",
                            fontFamily = AmiriFontFamily,
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                groupedHistory.forEach { (header, itemsList) ->
                    item(key = "history_header_$header") {
                        Text(
                            text = header,
                            fontFamily = AmiriFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80868B),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 6.dp)
                        )
                    }

                    items(itemsList, key = { "session_${it.id}" }) { session ->
                        val isSelected = session.id == currentSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) Color(0xFF282A2C) else Color.Transparent)
                                .clickable {
                                    onCloseDrawer()
                                    onSelectSession(session.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF8AB4F8) else Color(0xFF9AA0A6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = session.title.take(30),
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else Color(0xFFE8EAED),
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Google Profile Account Footer Card & Bottom Brand
            item(key = "drawer_profile_footer") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF333538))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1F20))
                        .border(1.dp, Color(0xFF333538), RoundedCornerShape(16.dp))
                        .clickable {
                            onCloseDrawer()
                            onOpenAuthDialog()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAccountAvatar(
                        userProfile = userProfile,
                        hasApiKey = hasPersonalApiKey,
                        size = 40.dp,
                        contentDescription = userProfile.displayName
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.displayName,
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                userProfile.isSignedIn -> {
                                    userProfile.email ?: (if (appLanguage == AppLanguage.ARABIC) "حساب متصل" else "Connected account")
                                }
                                hasPersonalApiKey -> {
                                    if (appLanguage == AppLanguage.ARABIC) "متصل عبر مفتاح API الشخصي" else "Connected via Personal API Key"
                                }
                                else -> {
                                    if (appLanguage == AppLanguage.ARABIC) "تسجيل الدخول بحساب Google أو API" else "Sign in with Google or API Key"
                                }
                            },
                            fontFamily = AmiriFontFamily,
                            fontSize = 11.sp,
                            color = if (userProfile.isSignedIn || hasPersonalApiKey) Color(0xFF8AB4F8) else Color(0xFF9AA0A6),
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (userProfile.isSignedIn || hasPersonalApiKey) Color(0xFF8AB4F8) else Color(0xFF9AA0A6),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Google Gemini • Ask Rameses",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF8AB4F8).copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
