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
import androidx.compose.material.icons.filled.AutoAwesome
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
    userProfile: com.example.data.model.UserProfile = com.example.data.model.UserProfile(),
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    selectedModelName: String = "gemini-3.1-flash-lite",
    onModelClick: () -> Unit = {},
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
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Navigation Menu Button (Start / Left in LTR) - Opens Chat History Drawer
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .testTag("menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = if (appLanguage == AppLanguage.ARABIC) "القائمة وسجل المحادثات" else "Menu & History",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Center: Royal App Title "اسأل رمسيس" / "Ask Rameses"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0C0C0C))
                .border(1.dp, RoyalGold.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ramses_user_photo),
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.dp, RoyalGold, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "اسأل رمسيس" else "Ask Rameses",
                fontFamily = if (appLanguage == AppLanguage.ARABIC) AmiriFontFamily else CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = RoyalGoldLight
            )
        }

        // 3. End Action: User Account Profile Avatar
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("profile_button")
        ) {
            UserAccountAvatar(
                userProfile = userProfile,
                hasApiKey = hasPersonalApiKey,
                size = 38.dp,
                contentDescription = if (appLanguage == AppLanguage.ARABIC) "الملف الشخصي والحسابات" else "Profile & Account"
            )
        }
    }
}
