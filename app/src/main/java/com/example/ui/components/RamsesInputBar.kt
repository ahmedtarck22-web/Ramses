package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RamsesInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onAttachClick: () -> Unit,
    onMicVoiceTypingClick: () -> Unit,
    onGeminiLiveClick: () -> Unit,
    selectedImageBitmap: Bitmap?,
    onClearAttachedImage: () -> Unit,
    isListeningVoiceTyping: Boolean = false,
    appLanguage: com.example.data.model.AppLanguage = com.example.data.model.AppLanguage.ARABIC,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val validPreviewBitmap = selectedImageBitmap?.takeIf {
            !it.isRecycled && it.width > 0 && it.height > 0
        }

        // Thumbnail preview for selected artifact image
        AnimatedVisibility(
            visible = validPreviewBitmap != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (validPreviewBitmap != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, RoyalGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = validPreviewBitmap.asImageBitmap(),
                            contentDescription = "الصورة المختارة للتحليل",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) {
                                    "صورة مرفقة جاهزة للتحليل"
                                } else {
                                    "Image attached for analysis"
                                },
                                fontFamily = AmiriFontFamily,
                                fontSize = 12.sp,
                                color = RoyalGold
                            )
                            Text(
                                text = if (appLanguage == com.example.data.model.AppLanguage.ARABIC) {
                                    "اضغط إرسال لعرض الصورة وتحليلها بدقة"
                                } else {
                                    "Tap send to display & analyze accurately"
                                },
                                fontFamily = AmiriFontFamily,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = onClearAttachedImage,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إزالة الصورة",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Liquid Glass Container matching HTML .glass-panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .border(
                    width = 1.dp,
                    color = if (isListeningVoiceTyping) EgyptianCyan else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(32.dp)
                ),
            color = SurfaceGlass,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // (+) Action Button (Photo / Artifact analysis or RAG)
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إرفاق صورة أو قطعة أثرية للتحليل",
                        tint = if (selectedImageBitmap != null) RoyalGold else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (isListeningVoiceTyping) {
                                if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "استمع لصوتك الآن..." else "Listening to your voice..."
                            } else {
                                if (appLanguage == com.example.data.model.AppLanguage.ARABIC) "اسأل رمسيس عن أي شيء..." else "Ask Rameses anything..."
                            },
                            fontFamily = AmiriFontFamily,
                            fontSize = 15.sp,
                            color = if (isListeningVoiceTyping) EgyptianCyan.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f)
                        )
                    }

                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputTextChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input_field"),
                        textStyle = TextStyle(
                            fontFamily = AmiriFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(RoyalGold),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() || selectedImageBitmap != null) {
                                    onSendMessage(inputText)
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )
                }

                // Action cluster: Mic, Gemini Live Waveform, and Send Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Send Button when text or image is present
                    if (inputText.isNotBlank() || selectedImageBitmap != null) {
                        IconButton(
                            onClick = {
                                onSendMessage(inputText)
                                focusManager.clearFocus()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A73E8))
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "إرسال الرسالة",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Mic Voice Button (Voice Typing -> Speech to text)
                    IconButton(
                        onClick = onMicVoiceTypingClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListeningVoiceTyping) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "تحدث صوتياً وسيكتب رمسيس ويرسل رسالتك",
                            tint = if (isListeningVoiceTyping) EgyptianCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Gemini Live Voice Call Button (Signature Waveform Pill)
                    IconButton(
                        onClick = onGeminiLiveClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("gemini_live_call_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            Color(0xFF1A73E8),
                                            Color(0xFF00E5FF),
                                            Color(0xFF8E24AA)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFF64B5F6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // 4 Gemini Live Waveform sound bars
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(13.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(17.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(9.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
