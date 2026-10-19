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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RoyalHeroSection(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestedPrompts = listOf(
        "⚔️ حدثني عن خطتك العسكرية في معركة قادش وأول معاهدة سلام في التاريخ",
        "☀️ كيف صممت معجزة تعامد الشمس على معبد أبو سمبل؟",
        "🏛️ ما هي أسرار الهندسة الفرعونية في تشييد المسلات والمعابد؟",
        "📜 قدم لي حكمة فرعونية لحل معضلة في حياتي اليومية"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Royal Badge Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            RoyalGold,
                            EgyptianCyan,
                            RoyalGoldLight,
                            RoyalGold
                        )
                    ),
                    shape = CircleShape
                )
                .padding(4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ramses_user_photo),
                contentDescription = "أيقونة اسأل رمسيس",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "اسأل رمسيس",
            fontFamily = CinzelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = RoyalGoldLight,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "«حكمة الفراعنة الخالدة ملتقية بعلوم العصر الحديث»",
            fontFamily = AmiriFontFamily,
            fontSize = 14.sp,
            color = EgyptianCyan,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Suggested Prompts Title
        Text(
            text = "استفتح مجلس الحوار بسؤال:",
            fontFamily = AmiriFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Suggested Prompt Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for ((index, prompt) in suggestedPrompts.withIndex()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPromptSelected(prompt) }
                        .testTag("suggested_prompt_$index"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceGlass
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prompt,
                            fontFamily = AmiriFontFamily,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            lineHeight = 20.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = RoyalGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
