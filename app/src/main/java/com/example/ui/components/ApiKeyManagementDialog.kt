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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.AppLanguage
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ApiKeyManagementDialog(
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: AppLanguage = AppLanguage.ARABIC
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    val hasKey = currentApiKey.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top row with close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = RoyalGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "مفتاح API الخاص بك" else "Your Personal API Key",
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = RoyalGoldLight
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App icon badge
                Image(
                    painter = painterResource(id = R.drawable.ask_ramses_app_icon_1791815852062),
                    contentDescription = "Ask Ramses",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, RoyalGold, RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "حصتك الخاصة: 1.5 مليون توكن مجاناً!" else "Your Quota: 1.5 Million Free Tokens!",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = EgyptianCyan,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (appLanguage == AppLanguage.ARABIC)
                        "كل مستخدم يحصل على مفتاح مجاني خاص به من Google AI Studio يمنحه 1,500,000 توكن مجاناً دون استهلاك كوتا مشتركة وبأعلى سرعة معالجة."
                    else
                        "Each user receives their own free key from Google AI Studio granting 1,500,000 free tokens without shared rate limits at maximum speed.",
                    fontFamily = AmiriFontFamily,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (hasKey) Color(0x2610B981) else Color(0x26F59E0B))
                        .border(
                            1.dp,
                            if (hasKey) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hasKey) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasKey) "المفتاح الشخصي مفعل بنجاح" else "لم يتم تعيين مفتاح API بعد",
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (hasKey) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Text(
                                text = if (hasKey) "سعة 1.5M Token متاحة بالكامل لحسابك" else "احصل على مفتاحك المجاني في دقيقة واحدة بالأسفل",
                                fontFamily = AmiriFontFamily,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // 3-Steps Guide Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1A102A43))
                        .border(0.5.dp, EgyptianCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "💡 كيف تحصل على مفتاحك المجاني (1.5M Token)؟",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = EgyptianCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1️⃣ اضغط زر 'فتح صفحة Google AI Studio' أدناه.\n" +
                                   "2️⃣ سجّل دخولك بحساب Google واضغط 'Create API key'.\n" +
                                   "3️⃣ انسخ الرمز والصقه هنا واضغط 'حفظ وتفعيل'.\n\n" +
                                   "✨ مجاني تماماً 100% وبدون أي بطاقة ائتمان أو اشتراكات.",
                            fontFamily = AmiriFontFamily,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direct button to get API key from Google AI Studio & Copy URL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "افتح: aistudio.google.com/apikey", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = PharaonicMidnight
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "فتح Google AI Studio",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Studio URL", "https://aistudio.google.com/apikey"))
                            Toast.makeText(context, "تم نسخ رابط الموقع للحافظة", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EgyptianCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EgyptianCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "نسخ الرابط",
                            fontFamily = AmiriFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field for API Key
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "الصق مفتاحك الشخصي هنا (Google Gemini API Key):",
                        fontFamily = AmiriFontFamily,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_dialog_input"),
                        placeholder = {
                            Text(
                                text = "AIzaSy...",
                                fontFamily = AmiriFontFamily,
                                color = TextSecondary.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = RoyalGold,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Paste from clipboard button
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = clipboard.primaryClip
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                            if (pastedText.isNotBlank()) {
                                                keyInput = pastedText.trim()
                                                Toast.makeText(context, "تم لصق المفتاح من الحافظة", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "لصق من الحافظة",
                                        tint = EgyptianCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Toggle Visibility button
                                IconButton(
                                    onClick = { isKeyVisible = !isKeyVisible },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (keyInput.isNotBlank()) {
                                    onSaveApiKey(keyInput.trim())
                                    Toast.makeText(context, "تم حفظ وتفعيل مفتاحك بنجاح!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons: Save & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasKey) {
                        OutlinedButton(
                            onClick = {
                                onSaveApiKey("")
                                keyInput = ""
                                Toast.makeText(context, "تم مسح المفتاح", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(0.4f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("مسح", fontFamily = AmiriFontFamily, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val trimmed = keyInput.trim()
                            if (trimmed.isNotBlank()) {
                                onSaveApiKey(trimmed)
                                Toast.makeText(context, "تم تفعيل مفتاحك بحصة 1.5M توكن!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "يرجى لصق مفتاح Google AI Studio", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = PharaonicMidnight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "حفظ وتفعيل المفتاح",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
