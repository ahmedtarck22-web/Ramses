package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.RamsesArtifact

@Composable
fun Ramses3DArtifactDialog(
    appLanguage: AppLanguage,
    onDismissRequest: () -> Unit,
    onAskRamses: (String) -> Unit
) {
    val isArabic = appLanguage == AppLanguage.ARABIC
    val artifacts = RamsesArtifact.ALL_ARTIFACTS
    var selectedArtifact by remember { mutableStateOf(artifacts[0]) }

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB070604))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume background touches */ }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .testTag("artifact_3d_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFDF73), Color(0xFFD4AF37), Color(0xFF7A5C10))
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            color = Color(0xFF0F0D09)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A210D))
                                .border(1.dp, Color(0xFFD4AF37), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = "3D SceneView",
                                tint = Color(0xFFFFDF73),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isArabic) "معرض الآثار الملكية 3D (SceneView)" else "Royal 3D Relics (SceneView)",
                                color = Color(0xFFFFE082),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = if (isArabic) "محرك Filament ثلاثي الأبعاد تفاعلي" else "Powered by Filament 3D Engine",
                                color = Color(0xFFA89462),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFE8C868)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed Artifact Selector Tabs (kept outside verticalScroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    artifacts.forEach { artifact ->
                        val isSelected = artifact.id == selectedArtifact.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFF38290D) else Color(0xFF19160E)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) Color(0xFFFFDF73) else Color(0xFF4A3E20),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedArtifact = artifact }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("artifact_chip_${artifact.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = artifact.iconEmoji,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = artifact.title(appLanguage),
                                    color = if (isSelected) Color(0xFFFFDF73) else Color(0xFFC7B992),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed 3D Viewport (kept outside verticalScroll so SurfaceView never crashes or intercepts scroll)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF443818), RoundedCornerShape(16.dp))
                ) {
                    RamsesSceneView(
                        modifier = Modifier.fillMaxSize(),
                        modelAssetPath = selectedArtifact.modelPath,
                        showControls = true,
                        initialAutoRotate = true,
                        idleAnimationEnabled = false
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xAA0F0D09)
                    ) {
                        Text(
                            text = if (isArabic) "اسحب بإصبعك للتدوير • استخدم الأزرار للتكبير" else "Drag with finger to rotate • Buttons to zoom",
                            color = Color(0xFFC7B992),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Description & Historical Fact
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("artifact_dialog_scroll"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedArtifact.title(appLanguage),
                            color = Color(0xFFFFE082),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF281F0B))
                                .border(0.5.dp, Color(0xFFD4AF37), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = selectedArtifact.dynasty(appLanguage),
                                color = Color(0xFFE8C868),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = selectedArtifact.description(appLanguage),
                        color = Color(0xFFDCD2BA),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E190D)),
                        border = BorderStroke(1.dp, Color(0xFF5A481B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Fact",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "حقيقة فرعونية تاريخية:" else "Historical Pharaonic Insight:",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedArtifact.fact(appLanguage),
                                    color = Color(0xFFD6C8A6),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action: Ask Ramses
                Button(
                    onClick = {
                        val prompt = if (isArabic) {
                            "حدثني بالتفصيل عن أسرار ${selectedArtifact.titleAr} وعلاقته بعصر رمسيس وتاريخ مصر القديمة."
                        } else {
                            "Tell me in detail about the secrets of the ${selectedArtifact.titleEn} and its significance in ancient Egyptian history."
                        }
                        onAskRamses(prompt)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ask_ramses_about_artifact_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37),
                        contentColor = Color(0xFF0F0D09)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Ask Ramses",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "استشر رمسيس عن هذا الأثر" else "Ask Ramses About This Relic",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
