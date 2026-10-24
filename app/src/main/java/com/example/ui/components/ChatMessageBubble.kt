package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RamsesBorder
import com.example.ui.theme.RamsesBubble
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubble

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onSpeakClick: (String) -> Unit,
    onOpenApiKeyClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onDismissErrorClick: () -> Unit = {},
    appLanguage: com.example.data.model.AppLanguage = com.example.data.model.AppLanguage.ARABIC,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = message.sender == MessageSender.USER

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User Message Bubble
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(UserBubble)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    fontFamily = AmiriFontFamily,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    lineHeight = 24.sp
                )
            }
        } else if (message.isError) {
            // Royal Error / Quota Exceeded Card with Action Buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .testTag("ramses_error_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF18151E)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFF59E0B),
                            Color(0xFFEF4444)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header with Warning icon and Dismiss button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33F59E0B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "تنبيه",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "تنبيه استهلاك الحد المؤقت (Quota)",
                                    fontFamily = AmiriFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "Google AI Studio • Rate Limit (15 RPM)",
                                    fontFamily = CinzelFontFamily,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissErrorClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إخفاء هذا التنبيه",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Message Body
                    Text(
                        text = message.text,
                        fontFamily = AmiriFontFamily,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenApiKeyClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalGold,
                                contentColor = PharaonicMidnight
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "🔑 إدخال / تغيير مفتاحك الخاص (مجاناً)" else "🔑 Enter / Change Personal Key (Free)",
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetryClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = RoyalGoldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "إعادة المحاولة" else "Retry",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "aistudio.google.com/apikey", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = EgyptianCyan
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EgyptianCyan.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "موقع AI Studio" else "Open AI Studio",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // King Ramses II Message Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .testTag("ramses_message_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RamsesBubble
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            RoyalGold.copy(alpha = 0.6f),
                            Color.Transparent,
                            EgyptianCyan.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header with Avatar and Royal Cartouche Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ramses_user_photo),
                                contentDescription = "اسأل رمسيس",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, RoyalGold, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "اسأل رمسيس" else "Ask Rameses",
                                    fontFamily = CinzelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = RoyalGoldLight
                                )
                                Text(
                                    text = if (message.isVoiceResponse) {
                                        if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "استجابة صوتية ملكية موجزة" else "Royal Spoken Response"
                                    } else {
                                        if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "حكمة فرعونية أصيلة" else "Authentic Pharaonic Wisdom"
                                    },
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 11.sp,
                                    color = EgyptianCyan
                                )
                            }
                        }

                        // Actions: Speak and Copy
                        Row {
                            IconButton(
                                onClick = { onSpeakClick(message.text) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "استماع بصوت رمسيس الثاني" else "Listen with Rameses's voice",
                                    tint = RoyalGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Ramses AI", message.text)
                                    clipboard.setPrimaryClip(clip)
                                    val toastMsg = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "تم نسخ الحكمة الملكية" else "Copied royal message"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "نسخ النص" else "Copy text",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Message Body
                    Text(
                        text = message.text,
                        fontFamily = AmiriFontFamily,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        lineHeight = 26.sp
                    )

                    // Grounding Sources (Cloud Firebase RAG small folders, File Search PDFs, and/or Google Search grounding)
                    if (message.groundingSources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val hasBookSource = message.groundingSources.any {
                            it.uri.startsWith("firestore://") ||
                                it.uri.startsWith("gs://") ||
                                it.uri.startsWith("fileSearchStores/") ||
                                it.uri.startsWith("projects/")
                        }
                        val sourcesHeader = if (hasBookSource) {
                            if (appLanguage == com.example.data.model.AppLanguage.ARABIC) {
                                context.getString(R.string.citations_books_and_search_ar)
                            } else {
                                context.getString(R.string.citations_books_and_search_en)
                            }
                        } else {
                            "المصادر والتوثيق الحي (Google Search):"
                        }
                        Text(
                            text = sourcesHeader,
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = RoyalGold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (source in message.groundingSources.take(5)) {
                                val isCloudBookRef = source.uri.startsWith("firestore://") ||
                                    source.uri.startsWith("gs://") ||
                                    source.uri.startsWith("fileSearchStores/") ||
                                    source.uri.startsWith("projects/")
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33102A43))
                                        .border(0.5.dp, EgyptianCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isCloudBookRef) {
                                                Toast.makeText(
                                                    context,
                                                    "📚 ${source.title}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else if (source.uri.isNotBlank()) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.uri))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    // Ignore if no browser
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = EgyptianCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = source.title.take(52),
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        color = TextSecondary
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
