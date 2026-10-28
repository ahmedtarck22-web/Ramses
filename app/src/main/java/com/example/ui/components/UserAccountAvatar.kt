package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import java.io.File

/**
 * Displays the user's account profile photo (from Google Sign-In photo URL or locally selected
 * profile avatar file) with a clean circular border and graceful initial/icon fallback.
 */
@Composable
fun UserAccountAvatar(
    userProfile: UserProfile,
    hasApiKey: Boolean = false,
    size: Dp = 36.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val avatarUrl = userProfile.avatarUrl?.trim().orEmpty()
    val hasCustomIdentity = userProfile.isSignedIn ||
        hasApiKey ||
        avatarUrl.isNotEmpty() ||
        (userProfile.displayName.isNotBlank() && userProfile.displayName != "زائر ملكي")

    val imageModel: Any? = remember(avatarUrl) {
        when {
            avatarUrl.isEmpty() -> null
            avatarUrl.startsWith("/") -> File(avatarUrl)
            else -> avatarUrl
        }
    }

    val borderBrush = if (hasCustomIdentity) {
        Brush.linearGradient(listOf(RoyalGold, EgyptianCyan))
    } else {
        Brush.linearGradient(listOf(Color(0xFF4285F4).copy(alpha = 0.65f), Color(0xFF8AB4F8).copy(alpha = 0.45f)))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (hasCustomIdentity) Color(0xFF1A365D) else Color(0x2A1A73E8)
            )
            .border(1.2.dp, borderBrush, CircleShape)
            .testTag("user_account_avatar"),
        contentAlignment = Alignment.Center
    ) {
        if (hasCustomIdentity) {
            val initial = userProfile.displayName
                .trim()
                .firstOrNull { !it.isWhitespace() }
                ?.uppercaseChar()
                ?.toString()
                ?: "U"
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.40f).sp,
                color = Color.White
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = contentDescription,
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(size * 0.68f)
            )
        }

        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription ?: userProfile.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .testTag("user_account_avatar_photo")
            )
        }
    }
}
