package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.ChatSession
import com.example.data.model.UserProfile
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceCard
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
    sessions: List<ChatSession>,
    currentSessionId: String,
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
    ModalDrawerSheet(
        drawerContainerColor = SurfaceCard,
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(18.dp)
        ) {
            // Top close and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }

                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "القائمة الملكية" else "Royal Menu",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RoyalGoldLight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // User Profile Card / Sign-in status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x26102A43))
                    .border(1.dp, RoyalGold.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .clickable {
                        onCloseDrawer()
                        onOpenAuthDialog()
                    }
                    .padding(12.dp)
                    .testTag("drawer_profile_card")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ask_ramses_app_icon_1791815852062),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userProfile.isSignedIn) userProfile.displayName else (if (appLanguage == AppLanguage.ARABIC) "تسجيل الدخول" else "Sign In"),
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (userProfile.isSignedIn) (userProfile.email ?: "Active") else "Google • Apple",
                            fontFamily = AmiriFontFamily,
                            fontSize = 12.sp,
                            color = if (userProfile.isSignedIn) Color(0xFF10B981) else EgyptianCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Main Actions
            DrawerMenuItem(
                icon = Icons.Default.Language,
                title = if (appLanguage == AppLanguage.ARABIC) "اللغة / Language" else "Language / اللغة",
                subtitle = if (appLanguage == AppLanguage.ARABIC) "العربية (انقر للتبديل إلى English)" else "English (Tap to switch to العربية)",
                onClick = onToggleLanguage
            )

            DrawerMenuItem(
                icon = Icons.Default.AddComment,
                title = AppStrings.newSession(appLanguage),
                onClick = {
                    onCloseDrawer()
                    onNewChatClick()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Phone,
                title = AppStrings.drawerVoiceCall(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "محادثة صوتية مستمرة فائقة السرعة" else "Continuous ultra-fast live dialogue",
                onClick = {
                    onCloseDrawer()
                    onVoiceCallClick()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.ViewInAr,
                title = if (appLanguage == AppLanguage.ARABIC) "معرض الآثار 3D (SceneView)" else "3D Relics Gallery (SceneView)",
                subtitle = if (appLanguage == AppLanguage.ARABIC) "استعراض تماثيل وأهرامات مجسمة بتفاعل لمسي" else "Interactive 3D pharaonic models & relics",
                onClick = {
                    onCloseDrawer()
                    onOpen3DArtifactsDialog()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Description,
                title = AppStrings.drawerKnowledge(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "تزويد رمسيس بملفات ووثائق تاريخية" else "Feed Rameses historical records",
                onClick = {
                    onCloseDrawer()
                    onOpenRagDialog()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Key,
                title = AppStrings.drawerKey(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "1.5M توكن مجاناً لكل مستخدم" else "1.5M free tokens per user",
                onClick = {
                    onCloseDrawer()
                    onOpenApiKeyDialog()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.AutoAwesome,
                title = if (appLanguage == AppLanguage.ARABIC) "البحث المباشر (Google Search)" else "Live Web Search (Google)",
                subtitle = if (isSearchGroundingEnabled) AppStrings.searchGroundingEnabled(appLanguage) else AppStrings.searchGroundingDisabled(appLanguage),
                onClick = onToggleSearchGrounding,
                trailing = {
                    Switch(
                        checked = isSearchGroundingEnabled,
                        onCheckedChange = { onToggleSearchGrounding() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RoyalGold,
                            checkedTrackColor = Color(0xFF102A43)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Chat History Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "سجل المحادثات:" else "Chat History:",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RoyalGoldLight,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                if (sessions.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAllChats,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = AppStrings.clearAllChats(appLanguage),
                            tint = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0x33102A43) else Color.Transparent)
                            .clickable {
                                onCloseDrawer()
                                onSelectSession(session.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                tint = if (isSelected) RoyalGold else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = session.title.take(24),
                                fontFamily = AmiriFontFamily,
                                fontSize = 13.sp,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Brand
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ASK RAMESES • اسأل رمسيس",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RoyalGold.copy(alpha = 0.8f)
                )
            }
        }
    }
}
