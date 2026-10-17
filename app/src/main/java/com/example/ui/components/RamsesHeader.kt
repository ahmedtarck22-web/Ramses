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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RamsesHeader(
    hasPersonalApiKey: Boolean,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    onToggleLanguage: () -> Unit,
    onProfileClick: () -> Unit,
    onVoiceCallClick: () -> Unit = {},
    onOpen3DArtifactsClick: () -> Unit = {},
    is3DAvatarMode: Boolean = true,
    onToggle3DAvatarMode: () -> Unit = {},
    onApiKeyClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile / User Button (Left)
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("profile_button")
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = if (appLanguage == AppLanguage.ARABIC) "الملف الشخصي والحسابات" else "Profile & Account",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Upper Part Center: Ramses App Icon + "ASK RAMESES / اسأل رمسيس" Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onApiKeyClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Official App Icon of Ramses
            Image(
                painter = painterResource(id = R.drawable.ask_ramses_app_icon_1791815852062),
                contentDescription = "Ask Rameses Icon",
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.2.dp, RoyalGold.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ASK RAMESES",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp,
                    color = TextPrimary
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "اسأل رمسيس" else "Royal AI Wisdom",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = RoyalGoldLight
                )
            }
        }

        // Right side: Voice Call + Language Switcher + API Key shortcut + Hamburger Menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // 3D Avatar Viewport Toggle Button (King Ramses 3D)
            IconButton(
                onClick = onToggle3DAvatarMode,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (is3DAvatarMode) Color(0x33D4AF37) else Color(0x18FFFFFF))
                    .border(1.dp, if (is3DAvatarMode) RoyalGold else Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("header_3d_avatar_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = if (appLanguage == AppLanguage.ARABIC) "تبديل عرض رمسيس 3D" else "Toggle 3D Ramses",
                    tint = if (is3DAvatarMode) RoyalGoldLight else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Language Switcher Badge (AR / EN)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22102A43))
                    .border(1.dp, RoyalGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onToggleLanguage() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("language_switch_button")
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "EN" else "عربي",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = RoyalGoldLight
                )
            }

            // Dedicated API Key icon button with 1.5M token indicator
            IconButton(
                onClick = onApiKeyClick,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("header_api_key_button")
            ) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = if (hasPersonalApiKey) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(7.dp)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API Key (1.5M Tokens)",
                        tint = if (hasPersonalApiKey) RoyalGold else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Three Horizontal Lines / Hamburger Menu
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
