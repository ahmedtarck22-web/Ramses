package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.CloudRagSyncState
import com.example.data.model.FileSearchPipelineState
import com.example.data.model.LinkedSearchInstance
import com.example.data.model.RagDocument
import com.example.data.model.SearchInstanceConfig
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
fun RagKnowledgeDialog(
    documents: List<RagDocument>,
    onAddDocument: (title: String, content: String, category: String) -> Unit,
    onToggleDocument: (RagDocument) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDismiss: () -> Unit,
    pipelineState: FileSearchPipelineState = FileSearchPipelineState.Idle,
    linkedInstance: LinkedSearchInstance? = null,
    searchConfig: SearchInstanceConfig = SearchInstanceConfig(),
    onUploadPdfUris: (List<Uri>) -> Unit = {},
    onSaveSearchConfig: (SearchInstanceConfig) -> Unit = {},
    cloudRagBooks: List<CloudRagBook> = emptyList(),
    cloudRagFolders: List<CloudRagFolder> = emptyList(),
    cloudRagSyncState: CloudRagSyncState = CloudRagSyncState.Idle,
    isUserSignedIn: Boolean = false,
    onSignInClick: () -> Unit = {},
    onSyncFiveBooksClick: () -> Unit = {},
    onConvertPdfUrisToCloud: (List<Uri>) -> Unit = {},
    onLoadFolderChunks: (String, (List<CloudRagChunk>) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isAddingNew by remember { mutableStateOf(false) }

    var titleInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("عام") }
    var contentInput by remember { mutableStateOf("") }

    val activeCount = documents.count { it.isEnabled }

    val textFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "وثيقة مستوردة"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        val n = cursor.getString(nameIdx)
                        if (!n.isNullOrBlank()) fileName = n
                    }
                }
                val textContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText().take(24_000)
                }?.trim().orEmpty()

                if (textContent.isNotBlank()) {
                    onAddDocument(fileName.substringBeforeLast("."), textContent, "ملف مرفوع")
                    Toast.makeText(context, "تم استيراد الملف وإضافته إلى قاعدة المعرفة: $fileName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "الملف فارغ أو غير نصي", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .imePadding()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("rag_dialog_scroll")
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "قاعدة المعرفة (RAG)",
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "زوّد رمسيس بمعلومات ووثائق مخصصة ليدمجها بدقة في إجاباته",
                    fontFamily = AmiriFontFamily,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26102A43))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EgyptianCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الوثائق النشطة في الـ RAG: $activeCount من ${documents.size}",
                            fontFamily = AmiriFontFamily,
                            fontSize = 13.sp,
                            color = EgyptianCyan
                        )
                    }

                    Button(
                        onClick = { isAddingNew = !isAddingNew },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = PharaonicMidnight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (isAddingNew) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAddingNew) "إلغاء" else "إضافة نص",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cloud Firebase Small-Folder RAG Hub (5 Indexed Books + Up to 1,000+ PDF Converter with 0 MB APK Bloat)
                CloudFirebaseRagSection(
                    books = cloudRagBooks,
                    folders = cloudRagFolders,
                    syncState = cloudRagSyncState,
                    isUserSignedIn = isUserSignedIn,
                    onSignInClick = onSignInClick,
                    onSyncFiveBooksClick = onSyncFiveBooksClick,
                    onConvertPdfUrisToCloud = onConvertPdfUrisToCloud,
                    onLoadFolderChunks = onLoadFolderChunks
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cloud Storage 5-PDF Upload & Vertex AI / Gemini File Search Linking Section
                RagFileSearchSection(
                    pipelineState = pipelineState,
                    linkedInstance = linkedInstance,
                    searchConfig = searchConfig,
                    onUploadPdfUris = onUploadPdfUris,
                    onSaveSearchConfig = onSaveSearchConfig
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Direct Document File Upload Button
                Button(
                    onClick = {
                        textFilePickerLauncher.launch(
                            arrayOf("text/*", "application/json", "text/csv", "text/markdown", "*/*")
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF132F4C),
                        contentColor = EgyptianCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("rag_upload_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "رفع ملف نصي من الجهاز (TXT / MD / JSON / CSV)",
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Form to add new document
                AnimatedVisibility(visible = isAddingNew) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "إضافة معرفة جديدة للـ RAG",
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = RoyalGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("عنوان الوثيقة", fontFamily = AmiriFontFamily) },
                            placeholder = { Text("مثال: خطة العمل، أسرار العائلة، وثيقة تاريخية...", fontFamily = AmiriFontFamily, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoyalGold,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = contentInput,
                            onValueChange = { contentInput = it },
                            label = { Text("محتوى الوثيقة أو النص", fontFamily = AmiriFontFamily) },
                            placeholder = { Text("اكتب أو الصق المعلومات التي تريد أن يعرفها رمسيس...", fontFamily = AmiriFontFamily, fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoyalGold,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (titleInput.isNotBlank() && contentInput.isNotBlank()) {
                                    onAddDocument(titleInput.trim(), contentInput.trim(), categoryInput.trim())
                                    titleInput = ""
                                    contentInput = ""
                                    isAddingNew = false
                                    Toast.makeText(context, "تمت إضافة الوثيقة إلى سياق الـ RAG", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "يرجى إدخال العنوان والمحتوى", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = PharaonicMidnight),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ الوثيقة في قاعدة المعرفة", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Documents List
                if (documents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد وثائق مضافة بعد.\nانقر على «إضافة وثيقة» لتزويد رمسيس بمعلوماتك الخاصة.",
                            fontFamily = AmiriFontFamily,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // Plain Column (no nested LazyColumn) inside outer verticalScroll
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        documents.forEach { doc ->
                            key(doc.id) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (doc.isEnabled) Color(0x26102A43) else Color(0x0DFFFFFF))
                                        .border(
                                            1.dp,
                                            if (doc.isEnabled) RoyalGold.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = if (doc.isEnabled) RoyalGold else TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = doc.title,
                                                    fontFamily = AmiriFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (doc.isEnabled) TextPrimary else TextSecondary
                                                )
                                                if (doc.isCloudPdfBook) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "PDF•GCS",
                                                        fontFamily = CinzelFontFamily,
                                                        fontSize = 9.sp,
                                                        color = EgyptianCyan
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = doc.content.take(65) + if (doc.content.length > 65) "..." else "",
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = doc.isEnabled,
                                                onCheckedChange = { onToggleDocument(doc) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = RoyalGold,
                                                    checkedTrackColor = Color(0xFF102A43)
                                                )
                                            )
                                            IconButton(
                                                onClick = { onDeleteDocument(doc.id) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف الوثيقة",
                                                    tint = TextSecondary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
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
