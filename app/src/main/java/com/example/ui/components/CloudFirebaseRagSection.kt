package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.CloudRagSyncState
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CloudFirebaseRagSection(
    books: List<CloudRagBook>,
    folders: List<CloudRagFolder>,
    syncState: CloudRagSyncState,
    isUserSignedIn: Boolean,
    onSignInClick: () -> Unit,
    onSyncFiveBooksClick: () -> Unit,
    onConvertPdfUrisToCloud: (List<Uri>) -> Unit,
    onLoadFolderChunks: (String, (List<CloudRagChunk>) -> Unit) -> Unit
) {
    var showExplorer by remember { mutableStateOf(false) }
    var expandedBookId by remember { mutableStateOf<String?>(null) }
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var loadedChunks by remember { mutableStateOf<Map<String, List<CloudRagChunk>>>(emptyMap()) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onConvertPdfUrisToCloud(uris)
        }
    }

    val totalChunks = books.sumOf { it.chunkCount }
    val isSyncing = syncState is CloudRagSyncState.Syncing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x2E0B2239))
            .border(1.dp, RoyalGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isUserSignedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = if (isUserSignedIn) Color(0xFF10B981) else RoyalGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cloud_rag_hub_title),
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = RoyalGoldLight
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "📚 الكتب المفهرسة: ${books.size} كتب • ${folders.size} مجلداً صغيراً • $totalChunks فقرة RAG موثقة بالصفحات",
            fontFamily = AmiriFontFamily,
            fontSize = 12.sp,
            color = EgyptianCyan
        )

        Text(
            text = stringResource(R.string.cloud_rag_zero_apk_badge),
            fontFamily = AmiriFontFamily,
            fontSize = 11.sp,
            color = Color(0xFF34D399)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sync Progress / Status Banner
        when (syncState) {
            is CloudRagSyncState.Syncing -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x331E3A5F))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = RoyalGold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncState.progressMessage,
                            fontFamily = AmiriFontFamily,
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (syncState.currentStep.toFloat() / syncState.totalSteps.coerceAtLeast(1)).coerceIn(0.08f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = RoyalGold,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            is CloudRagSyncState.Ready -> {
                Text(
                    text = "✔ ${syncState.statusMessage}",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = if (syncState.isCloudConnected) Color(0xFF34D399) else TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            is CloudRagSyncState.Error -> {
                Text(
                    text = "⚠ ${syncState.message}",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            CloudRagSyncState.Idle -> Unit
        }

        // Auth / Sync Action Row
        if (!isUserSignedIn) {
            Button(
                onClick = onSignInClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalGold,
                    contentColor = PharaonicMidnight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("cloud_rag_google_signin_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "سجّل الدخول بـ Google لمزامنة الـ 5 كتب ورفع حتى 1000 كتاب على سحابة Firebase",
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        } else {
            // Convert & Upload New PDFs Directly to Cloud Firebase Button
            Button(
                onClick = {
                    if (!isSyncing) {
                        multiPdfPicker.launch(arrayOf("application/pdf"))
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalGold,
                    contentColor = PharaonicMidnight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("cloud_rag_upload_pdfs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cloud_rag_upload_pdfs_btn),
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onSyncFiveBooksClick,
                enabled = !isSyncing,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EgyptianCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("cloud_rag_sync_five_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = EgyptianCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تحديث ومزامنة الكتب الخمسة مع سحابة Firebase الآن",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.5.sp,
                    color = EgyptianCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Toggle Small-Folder Explorer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1FFFFFFF))
                .clickable { showExplorer = !showExplorer }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("cloud_rag_toggle_explorer_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showExplorer) {
                    "إخفاء تقسيم الكتب والمجلدات الصغيرة"
                } else {
                    "استعراض الكتب الـ ${books.size} ومجلداتها الصغيرة (${folders.size} مجلد)"
                },
                fontFamily = AmiriFontFamily,
                fontSize = 12.sp,
                color = RoyalGoldLight
            )
            Icon(
                imageVector = if (showExplorer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = RoyalGoldLight,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = showExplorer) {
            // Plain Column (no nested LazyColumn) so parent verticalScroll works seamlessly everywhere
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                books.forEach { book ->
                    key(book.bookId) {
                        val isBookExpanded = expandedBookId == book.bookId
                        val bookFolders = remember(folders, book.bookId) {
                            folders.filter { it.bookId == book.bookId }.sortedBy { it.folderIndex }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x26081A2D))
                                .border(0.8.dp, RoyalGold.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        expandedBookId = if (isBookExpanded) null else book.bookId
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = RoyalGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = book.title,
                                            fontFamily = AmiriFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${book.author} • ${book.pageCount} ص • ${book.folderCount} مجلدات صغيرة • ${book.chunkCount} فقرة",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 10.5.sp,
                                            color = EgyptianCyan
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isBookExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isBookExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                for (folder in bookFolders) {
                                    val isFolderExpanded = expandedFolderId == folder.folderId
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x1FFFFFFF))
                                            .clickable {
                                                if (isFolderExpanded) {
                                                    expandedFolderId = null
                                                } else {
                                                    expandedFolderId = folder.folderId
                                                    if (!loadedChunks.containsKey(folder.folderId)) {
                                                        onLoadFolderChunks(folder.folderId) { list ->
                                                            loadedChunks = loadedChunks + (folder.folderId to list)
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = EgyptianCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "مجلد ${folder.folderIndex}: ${folder.folderTitle} (${folder.pageRange})",
                                                fontFamily = AmiriFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = RoyalGoldLight,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Text(
                                            text = "${folder.chunkCount} نسخ RAG • ${folder.topicSummary.take(90)}...",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )

                                        if (isFolderExpanded) {
                                            val folderChunks = loadedChunks[folder.folderId].orEmpty()
                                            Spacer(modifier = Modifier.height(4.dp))
                                            for (ch in folderChunks.take(4)) {
                                                Text(
                                                    text = "• [${ch.pageRange}] ${ch.content.take(120)}...",
                                                    fontFamily = AmiriFontFamily,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    modifier = Modifier.padding(vertical = 2.dp)
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
        }
    }
}
