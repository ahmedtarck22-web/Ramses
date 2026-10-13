package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
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
    onProfileClick: () -> Unit,
    onApiKeyClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile / User Button (Left)
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .size(44.dp)
                .testTag("profile_button")
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "الملف الشخصي والحسابات",
                tint = TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }

        // Upper Part Center: Ramses App Icon + "ASK RAMSES / اسأل رمسيس" Title
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
                contentDescription = "أيقونة رمسيس",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, RoyalGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ASK RAMSES",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 0.8.sp,
                    color = TextPrimary
                )
                Text(
                    text = "اسأل رمسيس",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = RoyalGoldLight
                )
            }
        }

        // Right side: API Key shortcut badge + Hamburger Menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Dedicated API Key icon button with 1.5M token indicator
            IconButton(
                onClick = onApiKeyClick,
                modifier = Modifier
                    .size(44.dp)
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
                        contentDescription = "مفتاح API الخاص بك (1.5M Token)",
                        tint = if (hasPersonalApiKey) RoyalGold else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Three Horizontal Lines / Hamburger Menu
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "القائمة الملكية",
                    tint = TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
