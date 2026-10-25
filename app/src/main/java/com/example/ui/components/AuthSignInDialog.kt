package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.AuthProvider
import com.example.data.model.UserProfile
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
fun GoogleLogo(modifier: Modifier = Modifier) {
    // Canvas drawing Google 'G' icon
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Simple stylized 4-color Google G mark
        drawArc(
            color = Color(0xFFEA4335), // Red
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            size = Size(w, h)
        )
        drawArc(
            color = Color(0xFF4285F4), // Blue
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(w, h)
        )
        drawArc(
            color = Color(0xFF34A853), // Green
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(w, h)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.3f,
            center = Offset(w / 2f, h / 2f)
        )
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(w * 0.45f, h * 0.4f),
            size = Size(w * 0.5f, h * 0.2f)
        )
    }
}

@Composable
fun AppleLogo(modifier: Modifier = Modifier) {
    // Stylized clean Apple icon
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Body
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.25f)
            cubicTo(w * 0.65f, h * 0.15f, w * 0.85f, h * 0.25f, w * 0.85f, h * 0.5f)
            cubicTo(w * 0.85f, h * 0.75f, w * 0.65f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.35f, h * 0.95f, w * 0.15f, h * 0.75f, w * 0.15f, h * 0.5f)
            cubicTo(w * 0.15f, h * 0.25f, w * 0.35f, h * 0.15f, w * 0.5f, h * 0.25f)
            close()
        }
        drawPath(path, color = Color.White)

        // Leaf
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.45f, h * 0.05f),
            size = Size(w * 0.2f, h * 0.18f)
        )
    }
}

@Composable
fun AuthSignInDialog(
    userProfile: UserProfile,
    onSignInWithGoogle: (email: String, name: String) -> Unit,
    onSignInWithApple: (email: String, name: String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onGoogleSignInClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("auth_dialog_scroll")
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الحساب الملكي",
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = RoyalGoldLight
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App Icon
                Image(
                    painter = painterResource(id = R.drawable.ramses_user_photo),
                    contentDescription = "اسأل رمسيس",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, RoyalGold, RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "اسأل رمسيس (Ask Ramses)",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )

                Text(
                    text = "سجّل دخولك لحفظ وتزامن محادثاتك واستدعاء الحكمة الملكية",
                    fontFamily = AmiriFontFamily,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (userProfile.isSignedIn) {
                    // Active Signed-in User State Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x26102A43))
                            .border(1.dp, EgyptianCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "متصل بنجاح",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF10B981)
                                    )
                                }

                                Text(
                                    text = if (userProfile.provider == AuthProvider.GOOGLE) "Google" else "Apple",
                                    fontFamily = CinzelFontFamily,
                                    fontSize = 12.sp,
                                    color = RoyalGold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = userProfile.displayName,
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )

                            if (userProfile.email != null) {
                                Text(
                                    text = userProfile.email,
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = {
                            onSignOut()
                            Toast.makeText(context, "تم تسجيل الخروج", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تسجيل الخروج",
                            fontFamily = AmiriFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Sign-in Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Notice about Cloud Sync
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x26102A43))
                                .border(1.dp, EgyptianCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "المصادقة السحابية عبر Firebase مهيأة ومحمية بقواعد أمان صارمة لحفظ وتزامن محادثاتك وسجلّك الملكي.",
                                fontFamily = AmiriFontFamily,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 1. Google Sign-In via Credential Manager
                        Button(
                            onClick = {
                                onGoogleSignInClick()
                            },
                            enabled = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_signin_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                GoogleLogo(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "تسجيل الدخول عبر Google",
                                    fontFamily = AmiriFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1F1F1F)
                                )
                            }
                        }

                        // 2. Apple Sign-In (Clearly Disabled until real OAuth backend is configured)
                        Button(
                            onClick = {
                                Toast.makeText(context, "تسجيل الدخول عبر Apple غير متاح على هذه المنصة", Toast.LENGTH_LONG).show()
                            },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("apple_signin_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFF1E2125),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                AppleLogo(modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Apple Sign-In (غير مهيأ)",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Primary Action: Continue as Royal Guest with local storage
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("guest_mode_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalGold,
                                contentColor = PharaonicMidnight
                            )
                        ) {
                            Text(
                                text = "المتابعة كزائر ملكي (حفظ محلي آمن)",
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
