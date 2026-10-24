package com.example.ui.components

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FileSearchDefaults
import com.example.data.model.FileSearchPipelineState
import com.example.data.model.LinkedSearchInstance
import com.example.data.model.SearchInstanceConfig
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Dedicated Compose panel inside [RagKnowledgeDialog] for uploading up to 5 PDF books
 * to Google Cloud Storage (`gs://...`) and linking them to a Vertex AI / Gemini File Search instance.
 */
@Composable
fun RagFileSearchSection(
    pipelineState: FileSearchPipelineState,
    linkedInstance: LinkedSearchInstance?,
    searchConfig: SearchInstanceConfig,
    onUploadPdfUris: (List<Uri>) -> Unit,
    onSaveSearchConfig: (SearchInstanceConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfigEditor by remember { mutableStateOf(false) }
    var dataStoreIdInput by remember(searchConfig.vertexSearchDataStoreId) {
        mutableStateOf(searchConfig.vertexSearchDataStoreId)
    }
    var fileSearchStoreInput by remember(searchConfig.geminiFileSearchStoreName) {
        mutableStateOf(searchConfig.geminiFileSearchStoreName)
    }
    var serverProxyInput by remember(searchConfig.serverProxyBaseUrl) {
        mutableStateOf(searchConfig.serverProxyBaseUrl)
    }

    val isBusy = pipelineState is FileSearchPipelineState.UploadingToCloudStorage ||
        pipelineState is FileSearchPipelineState.LinkingToSearchInstance

    // Zero-permission multi-PDF picker (Google Play compliant)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val selected = uris.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)
            if (uris.size > FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT) {
                Toast.makeText(
                    context,
                    "تم اختيار أول 5 كتب PDF للرفع والفهرسة السحابية",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onUploadPdfUris(selected)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x290E2744))
            .border(1.dp, RoyalGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.rag_file_search_section_title),
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = RoyalGoldLight,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFFFFF))
                    .clickable { showConfigEditor = !showConfigEditor }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("rag_toggle_search_config_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.rag_configure_search_instance),
                        tint = EgyptianCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Search",
                        fontFamily = CinzelFontFamily,
                        fontSize = 10.sp,
                        color = EgyptianCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                pdfPickerLauncher.launch(arrayOf(FileSearchDefaults.PDF_MIME_TYPE))
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = RoyalGold,
                contentColor = PharaonicMidnight
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("rag_upload_five_pdfs_button")
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = PharaonicMidnight,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.rag_upload_five_pdfs_button),
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Pipeline Status Banner
        when (pipelineState) {
            is FileSearchPipelineState.UploadingToCloudStorage -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "جاري رفع الكتاب (${pipelineState.currentBookNumber}/${pipelineState.totalBooks}) إلى Google Cloud Storage: ${pipelineState.fileName}",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = EgyptianCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pipelineState.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = RoyalGold,
                    trackColor = Color(0x33FFFFFF)
                )
            }

            is FileSearchPipelineState.LinkingToSearchInstance -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "جاري ربط وفهرسة ${pipelineState.uploadedBooks.size} كتب PDF مع محرك البحث (${pipelineState.targetDescription})...",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    color = RoyalGoldLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = EgyptianCyan,
                    trackColor = Color(0x33FFFFFF)
                )
            }

            is FileSearchPipelineState.Ready -> {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مرتبط (${pipelineState.instance.books.size} كتب PDF) • ${pipelineState.instance.storeResourceName.take(42)}",
                        fontFamily = AmiriFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }

            is FileSearchPipelineState.Error -> {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pipelineState.message.take(120),
                        fontFamily = AmiriFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            FileSearchPipelineState.Idle -> {
                if (linkedInstance != null && linkedInstance.books.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الكتب السحابية المرتبطة: ${linkedInstance.books.size} • gs://${searchConfig.normalizedBucketName()}",
                        fontFamily = AmiriFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Collapsible Search Instance Configuration Editor
        AnimatedVisibility(visible = showConfigEditor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                OutlinedTextField(
                    value = dataStoreIdInput,
                    onValueChange = { dataStoreIdInput = it },
                    label = { Text("Vertex AI Search DataStore ID (اختياري)", fontFamily = AmiriFontFamily, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_vertex_datastore_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalGold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = fileSearchStoreInput,
                    onValueChange = { fileSearchStoreInput = it },
                    label = { Text("Gemini FileSearchStore (مثال: fileSearchStores/...)", fontFamily = AmiriFontFamily, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_gemini_store_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalGold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = serverProxyInput,
                    onValueChange = { serverProxyInput = it },
                    label = { Text("Server RAG Proxy URL (لإبقاء المفاتيح على الخادم)", fontFamily = AmiriFontFamily, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_server_proxy_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalGold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSaveSearchConfig(
                            searchConfig.copy(
                                vertexSearchDataStoreId = dataStoreIdInput.trim(),
                                geminiFileSearchStoreName = fileSearchStoreInput.trim(),
                                serverProxyBaseUrl = serverProxyInput.trim()
                            )
                        )
                        showConfigEditor = false
                        Toast.makeText(context, "تم حفظ إعدادات محرك البحث بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rag_save_search_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF132F4C),
                        contentColor = EgyptianCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.rag_save_search_config),
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
