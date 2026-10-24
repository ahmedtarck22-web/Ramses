package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.AvatarRigConfig
import com.example.data.model.RigJoint
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.EgyptianCyan
import com.example.ui.theme.PharaonicMidnight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Interactive 3D Rigging Studio & Custom .GLB Model Importer Dialog.
 *
 * Allows the user to:
 * 1. Import any custom 3D model (.glb) from device storage safely without storage permissions.
 * 2. Visually place & drag skeleton Rig lines & joints ("وضع خطوط الـ Rig": Head, Jaw, Neck, Spine, Shoulders, Elbows, Hands).
 * 3. Adjust rotation, scale, jaw/mouth lip-sync sensitivity, and live skeleton line visibility.
 * 4. Read the complete step-by-step Arabic/English guide on 3D Rigging (Mixamo, AccuRIG, Blender, and in-app).
 */
@Composable
fun RiggingStudioDialog(
    initialConfig: AvatarRigConfig,
    appLanguage: AppLanguage = AppLanguage.ARABIC,
    onSaveConfig: (AvatarRigConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isArabic = appLanguage == AppLanguage.ARABIC

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Interactive Rig Editor, 1 = Step-by-Step Guide
    var joints by remember(initialConfig) { mutableStateOf(initialConfig.joints) }
    var customModelPath by remember(initialConfig) { mutableStateOf(initialConfig.customModelPath) }
    var customModelName by remember(initialConfig) { mutableStateOf(initialConfig.customModelName) }
    var showSkeletonOverlay by remember(initialConfig) { mutableStateOf(initialConfig.showSkeletonLinesOnAvatar) }
    var rotationY by remember(initialConfig) { mutableFloatStateOf(initialConfig.modelRotationY) }
    var modelScale by remember(initialConfig) { mutableFloatStateOf(initialConfig.modelScale) }
    var lipSyncIntensity by remember(initialConfig) { mutableFloatStateOf(initialConfig.lipSyncIntensity) }
    var gestureIntensity by remember(initialConfig) { mutableFloatStateOf(initialConfig.gestureIntensity) }
    var selectedJointId by remember { mutableStateOf<String?>("jaw_mouth") }

    // Zero-permission SAF Document Picker for .glb / 3D binary files
    val glbFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val imported = copyGlbUriToInternalStorage(context, uri)
            if (imported != null) {
                customModelPath = imported.first
                customModelName = imported.second
                // Standard Mixamo / AccuRIG / Blender exports usually face +Z (0 degrees)
                rotationY = 0f
                Toast.makeText(
                    context,
                    if (isArabic) "تم استيراد المجسم بنجاح: ${imported.second}" else "Imported 3D model: ${imported.second}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    if (isArabic) "تعذر قراءة ملف المجسم. تأكد أنه بصيغة .glb صالحة" else "Could not read file. Ensure it is a valid .glb model",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050A18),
                            Color(0xFF0A152C),
                            Color(0xFF050A18)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = RoyalGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isArabic) "استوديو الـ Rig وإضافة ملفات 3D" else "3D Rig Studio & File Importer",
                                fontFamily = AmiriFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = RoyalGoldLight
                            )
                            Text(
                                text = if (isArabic) "ضع خطوط الهيكل والمفاصل أو استورد مجسمات .glb" else "Place skeleton bone lines or import rigged .glb models",
                                fontFamily = AmiriFontFamily,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("rig_studio_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isArabic) "إغلاق" else "Close",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Tabs: [1. محرر خطوط الـ Rig ورفع الملفات] | [2. شرح طريقة الـ Rig خطوة بخطوة]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF111C33))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TabPill(
                        selected = selectedTab == 0,
                        icon = Icons.Default.AccountTree,
                        title = if (isArabic) "وضع خطوط الـ Rig + ملف .GLB" else "Rig Lines & .GLB Import",
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rig_tab_editor")
                    )
                    TabPill(
                        selected = selectedTab == 1,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = if (isArabic) "طريقة عمل الـ Rig وإضافة الملفات" else "Step-by-Step Rig Guide",
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rig_tab_guide")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Custom .GLB File Import Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                            imageVector = Icons.Default.ViewInAr,
                                            contentDescription = null,
                                            tint = EgyptianCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (isArabic) "المجسم الثلاثي الأبعاد النشط:" else "Active 3D Avatar File:",
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = customModelName ?: if (isArabic) "Ramses.final.glb (المجسم الملكي المدمج)" else "Ramses.final.glb (Built-in Royal Avatar)",
                                                fontFamily = CinzelFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (customModelPath != null) EgyptianCyan else RoyalGoldLight
                                            )
                                        }
                                    }

                                    if (customModelPath != null) {
                                        IconButton(
                                            onClick = {
                                                customModelPath = null
                                                customModelName = null
                                                rotationY = 90f
                                                modelScale = 0.54f
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = if (isArabic) "استعادة مجسم رمسيس" else "Restore default",
                                                tint = RoyalGold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        glbFileLauncher.launch(
                                            arrayOf("model/gltf-binary", "application/octet-stream", "*/*")
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoyalGold,
                                        contentColor = PharaonicMidnight
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("import_glb_file_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isArabic) "إضافة / استيراد ملف مجسم (.glb) من الجهاز" else "Import Custom 3D Model (.glb) File",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // 2. Interactive Rig Skeleton Lines Canvas ("وضع وتحريك خطوط الـ Rig")
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF081226),
                            border = BorderStroke(1.dp, EgyptianCyan.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val activeJoint = joints.firstOrNull { it.id == selectedJointId }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isArabic) "لوحة وضع خطوط الـ Rig (اسحب النقاط لضبط العظام)" else "Interactive Rig Lines (Drag joints to align)",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EgyptianCyan
                                    )
                                    Text(
                                        text = activeJoint?.let { if (isArabic) it.labelAr else it.labelEn } ?: "",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        color = RoyalGoldLight
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                InteractiveRigCanvas(
                                    joints = joints,
                                    selectedJointId = selectedJointId,
                                    onSelectJoint = { selectedJointId = it },
                                    onUpdateJoint = { id, newX, newY ->
                                        selectedJointId = id
                                        joints = joints.map { j ->
                                            if (j.id == id) j.copy(x = newX, y = newY) else j
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(270.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF040915))
                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                                        .testTag("interactive_rig_canvas")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isArabic) "إظهار خطوط الـ Rig مباشرة فوق المجسم 3D" else "Show live Rig lines overlay on 3D Avatar",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = showSkeletonOverlay,
                                        onCheckedChange = { showSkeletonOverlay = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = EgyptianCyan,
                                            checkedTrackColor = Color(0xFF0E3A53)
                                        )
                                    )
                                }
                            }
                        }

                        // 3. Model Orientation, Scale & Rig Motion Controls
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isArabic) "معايرة اتجاه المجسم وقوة حركة المفاصل" else "Model Orientation & Rig Motion Calibration",
                                    fontFamily = AmiriFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = RoyalGoldLight
                                )

                                // Rotation Y Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isArabic) "زاوية دوران المجسم (Y): ${rotationY.roundToInt()}°" else "Model Y Rotation: ${rotationY.roundToInt()}°",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = if (isArabic) "(0° لـ Mixamo/Blender • 90° لرمسيس)" else "(0° Mixamo • 90° Ramses)",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        color = EgyptianCyan
                                    )
                                }
                                Slider(
                                    value = rotationY,
                                    onValueChange = { rotationY = it },
                                    valueRange = -180f..180f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = RoyalGold,
                                        activeTrackColor = RoyalGold
                                    )
                                )

                                // Scale Slider
                                Text(
                                    text = if (isArabic) "حجم المجسم (Scale): ${(modelScale * 100).roundToInt()}%" else "Model Scale: ${(modelScale * 100).roundToInt()}%",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = modelScale,
                                    onValueChange = { modelScale = it },
                                    valueRange = 0.20f..1.40f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = EgyptianCyan,
                                        activeTrackColor = EgyptianCyan
                                    )
                                )

                                // Lip-Sync & Gesture Intensity Slider
                                Text(
                                    text = if (isArabic) "قوة تحريك الفك واليدين أثناء الكلام: ${(lipSyncIntensity * 100).roundToInt()}%" else "Jaw Lip-Sync & Gesture Strength: ${(lipSyncIntensity * 100).roundToInt()}%",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = lipSyncIntensity,
                                    onValueChange = {
                                        lipSyncIntensity = it
                                        gestureIntensity = it
                                    },
                                    valueRange = 0.30f..1.80f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = RoyalGoldLight,
                                        activeTrackColor = RoyalGoldLight
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // TAB 2: Comprehensive Step-by-Step Arabic/English Guide for Adding Files & 3D Rigging
                    RiggingStepByStepGuide(
                        isArabic = isArabic,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            joints = AvatarRigConfig.defaultJoints()
                            lipSyncIntensity = 1.0f
                            gestureIntensity = 1.0f
                            rotationY = if (customModelPath != null) 0f else 90f
                            modelScale = 0.54f
                        },
                        border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.42f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = RoyalGoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) "إعادة ضبط" else "Reset Rig",
                            fontFamily = AmiriFontFamily,
                            fontSize = 13.sp,
                            color = RoyalGoldLight
                        )
                    }

                    Button(
                        onClick = {
                            val updated = AvatarRigConfig(
                                customModelPath = customModelPath,
                                customModelName = customModelName,
                                joints = joints,
                                showSkeletonLinesOnAvatar = showSkeletonOverlay,
                                lipSyncIntensity = lipSyncIntensity,
                                gestureIntensity = gestureIntensity,
                                modelRotationY = rotationY,
                                modelScale = modelScale
                            )
                            onSaveConfig(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = PharaonicMidnight
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.58f)
                            .height(48.dp)
                            .testTag("save_rig_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) "حفظ وتطبيق الـ Rig" else "Save & Apply Rig",
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

@Composable
private fun TabPill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color(0x33D4AF37) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) RoyalGold else Color.Transparent,
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) RoyalGoldLight else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = AmiriFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                color = if (selected) RoyalGoldLight else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InteractiveRigCanvas(
    joints: List<RigJoint>,
    selectedJointId: String?,
    onSelectJoint: (String) -> Unit,
    onUpdateJoint: (id: String, newX: Float, newY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingJointId by remember { mutableStateOf<String?>(null) }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(joints, canvasSize) {
                detectTapGestures { tapOffset ->
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectTapGestures
                    val nearest = joints.minByOrNull { j ->
                        hypot(j.x * canvasSize.width - tapOffset.x, j.y * canvasSize.height - tapOffset.y)
                    }
                    if (nearest != null) {
                        val dist = hypot(
                            nearest.x * canvasSize.width - tapOffset.x,
                            nearest.y * canvasSize.height - tapOffset.y
                        )
                        if (dist <= 64f) {
                            onSelectJoint(nearest.id)
                        }
                    }
                }
            }
            .pointerInput(joints, canvasSize) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures
                        val nearest = joints.minByOrNull { j ->
                            hypot(j.x * canvasSize.width - startOffset.x, j.y * canvasSize.height - startOffset.y)
                        }
                        if (nearest != null) {
                            val dist = hypot(
                                nearest.x * canvasSize.width - startOffset.x,
                                nearest.y * canvasSize.height - startOffset.y
                            )
                            if (dist <= 75f) {
                                draggingJointId = nearest.id
                                onSelectJoint(nearest.id)
                            }
                        }
                    },
                    onDragEnd = { draggingJointId = null },
                    onDragCancel = { draggingJointId = null },
                    onDrag = { change, dragAmount ->
                        val activeId = draggingJointId ?: return@detectDragGestures
                        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures
                        change.consume()
                        val current = joints.firstOrNull { it.id == activeId } ?: return@detectDragGestures
                        val nextX = (current.x + dragAmount.x / canvasSize.width).coerceIn(0.06f, 0.94f)
                        val nextY = (current.y + dragAmount.y / canvasSize.height).coerceIn(0.06f, 0.94f)
                        onUpdateJoint(activeId, nextX, nextY)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // 1. Subtle Blueprint Grid + Center Symmetry Axis Line
        val gridStep = w / 8f
        var gx = gridStep
        while (gx < w) {
            drawLine(
                color = Color(0x1400E5FF),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f
            )
            gx += gridStep
        }
        var gy = h / 6f
        while (gy < h) {
            drawLine(
                color = Color(0x1400E5FF),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
            gy += h / 6f
        }

        // Center symmetry guide
        drawLine(
            color = RoyalGold.copy(alpha = 0.25f),
            start = Offset(w * 0.5f, 0f),
            end = Offset(w * 0.5f, h),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        // Subtle Silhouette Guide behind the Rig Lines
        drawCircle(
            color = Color(0x18D4AF37),
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.26f)
        )

        val jointMap = joints.associateBy { it.id }

        // 2. Draw Connected Skeleton Bone Lines ("خطوط الـ Rig")
        for ((fromId, toId) in AvatarRigConfig.BONE_CONNECTIONS) {
            val from = jointMap[fromId] ?: continue
            val to = jointMap[toId] ?: continue
            val p1 = Offset(from.x * w, from.y * h)
            val p2 = Offset(to.x * w, to.y * h)

            // Outer glow
            drawLine(
                color = EgyptianCyan.copy(alpha = 0.28f),
                start = p1,
                end = p2,
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )
            // Core bone line
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(RoyalGoldLight, EgyptianCyan),
                    start = p1,
                    end = p2
                ),
                start = p1,
                end = p2,
                strokeWidth = 4.2f,
                cap = StrokeCap.Round
            )
        }

        // 3. Draw Draggable Joint Handles ("نقاط المفاصل")
        for (joint in joints) {
            val center = Offset(joint.x * w, joint.y * h)
            val isSelected = joint.id == selectedJointId
            val isJaw = joint.id == "jaw_mouth"

            if (isSelected) {
                drawCircle(
                    color = RoyalGold.copy(alpha = 0.35f),
                    radius = 22f,
                    center = center
                )
            }

            drawCircle(
                color = when {
                    isJaw -> RoyalGoldLight
                    isSelected -> Color.White
                    else -> EgyptianCyan
                },
                radius = if (isSelected) 11f else 8.5f,
                center = center
            )

            drawCircle(
                color = PharaonicMidnight,
                radius = if (isSelected) 4.5f else 3.5f,
                center = center
            )

            if (isJaw) {
                drawCircle(
                    color = RoyalGold,
                    radius = 15f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
private fun RiggingStepByStepGuide(
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GuideStepCard(
            badge = "1",
            title = if (isArabic) "أولاً: إضافة الملفات ووضع خطوط الـ Rig من داخل التطبيق مباشرة" else "1. Direct In-App .GLB Import & Rig Line Placement",
            body = if (isArabic) {
                """• اضغط على تبويب (وضع خطوط الـ Rig + ملف .GLB) بالأعلى ثم اضغط زر (إضافة / استيراد ملف مجسم .glb).
• اختر أي مجسم ثلاثي الأبعاد بصيغة (.glb) من هاتفك.
• في لوحة (وضع خطوط الـ Rig)، اسحب النقاط والخطوط بيدك لتطابق مفاصل شخصيتك:
  - ضع نقطة (الفك والفم) فوق فم التمثال أو الشخصية مباشرة لضبط موجات وحركة النطق.
  - ضع خطوط (الرقبة، الكتفين، الكوعين، اليدين، والعمود الفقري) على أماكنها في المجسم.
• إذا كان المجسم يلتفت للجانب، حرّك شريط (زاوية دوران المجسم Y) إلى 0° أو 90° ثم اضغط (حفظ وتطبيق الـ Rig)."""
            } else {
                """• Open the 'Rig Lines & .GLB Import' tab and tap 'Import Custom 3D Model (.glb) File'.
• Drag the connected cyan/gold bone lines and joint handles on the canvas so 'Jaw / Mouth', 'Neck', 'Shoulders', 'Elbows', and 'Hands' align with your model.
• Adjust the Y-Rotation slider (0° for Mixamo/Blender exports, 90° for Ramses) and tap 'Save & Apply Rig'."""
            }
        )

        GuideStepCard(
            badge = "2",
            title = if (isArabic) "ثانياً: عمل Rig كامل تلقائي بوضع النقاط والخطوط مجاناً (Mixamo / AccuRIG)" else "2. Auto-Rigging Static Statues with Bone Lines (Mixamo / AccuRIG)",
            body = if (isArabic) {
                """إذا كان لديك تمثال ثابت وتريد تحريك عظام الجسم بالكامل (Skeletal Armature):
1. افتح موقع (Adobe Mixamo) أو برنامج (ActorCore AccuRIG المجاني).
2. ارفع ملف المجسم (.fbx أو .obj — وإذا كان .glb حوّله في Blender إلى .fbx أولاً).
3. ستظهر لك شاشة وضع دوائر وخطوط الـ Rig على المجسم:
   • Chin (الذقن)  • Wrists (المعصمين)  • Elbows (الكوعين)  • Knees (الركبتين)  • Groin (الحوض).
4. اضغط Auto-Rig ليقوم النظام ببناء الهيكل العظمي (الخطوط والأوزان Skin Weights) تلقائياً في ثوانٍ!
5. اختر حركة هادئة مثل (Talking أو Breathing Idle) وحمّل الملف، ثم افتحه في Blender واختر:
   File ➔ Export ➔ glTF 2.0 (.glb) مع تفعيل (Include: Armature + Animation + Shape Keys).
6. استورد ملف الـ (.glb) الناتج هنا في التطبيق وسيقوم محرك SceneView بتشغيل عظام الـ Rig تلقائياً!"""
            } else {
                """To rig a static statue with full skeletal bones:
1. Upload your mesh to Adobe Mixamo or ActorCore AccuRIG.
2. Place the joint markers (Chin, Wrists, Elbows, Knees, Groin) to generate the skeleton bone lines automatically.
3. Pick an 'Idle' or 'Talking' animation, export via Blender as 'glTF 2.0 (.glb)' with Skinning & Animations checked, and import it directly here—the app automatically detects and plays embedded Armature animations!"""
            }
        )

        GuideStepCard(
            badge = "3",
            title = if (isArabic) "ثالثاً: وضع خطوط العظام يدوياً في Blender + حركة الفم (Shape Keys)" else "3. Custom Bone Lines & Mouth Lip-Sync in Blender",
            body = if (isArabic) {
                """لوضع خطوط العظام (Bones) يدوياً بدقة احترافية في Blender:
1. افتح مجسمك في Blender واضغط (Shift + A ➔ Armature ➔ Single Bone أو Human Meta-Rig).
2. فعّل خيار (In Front) من خصائص الـ Armature لترى خطوط العظام فوق المجسم بوضوح.
3. ادخل وضع التعديل (Tab - Edit Mode) واستخدم (E - Extrude) لمد خطوط العظام للرأس والفك والذراعين.
4. اربط المجسم بخطوط العظام: حدد المجسم ثم خطوط الـ Armature واضغط (Ctrl + P ➔ With Automatic Weights).
5. لحركة الشفاه الدقيقة مع صوت Gemini Live: أضف في قسم (Object Data Properties ➔ Shape Keys):
   • Basis (الوضع المغلق)
   • Key 1: فتح الفم/الفك (Mouth Open)
   • Key 2: إشارة اليد الملكية (Hand Gesture)
6. صدّر الملف بصيغة (.glb) وأضفه للتطبيق من زر الاستيراد بالأعلى، أو ضعه داخل مجلد المشروع:
   app/src/main/assets/models/"""
            } else {
                """In Blender:
1. Press Shift + A ➔ Armature (enable 'In Front' under Viewport Display to see bone lines).
2. Extrude (E) bones along the spine, neck, jaw, and arms, then select Mesh + Armature and press Ctrl + P ➔ With Automatic Weights.
3. Optionally add 2 Shape Keys (Mouth Open & Hand Gesture) for direct GPU lip-sync morphing, then export as glTF 2.0 (.glb)."""
            }
        )

        GuideStepCard(
            badge = "4",
            title = if (isArabic) "رابعاً: إضافة ملفات المعلومات والوثائق النصية (RAG)" else "4. Adding Knowledge Files (RAG Documents)",
            body = if (isArabic) {
                """لإضافة ملفات معلومات خاصة بك (.txt, .md, .json, .csv) لكي يقرأها رمسيس ويجيب منها في الشات أو في محادثة Gemini Live الصوتية:
• افتح القائمة الجانبية (☰) ➔ اختر (قاعدة المعرفة ورفع الملفات RAG) ➔ اضغط (رفع ملف من الجهاز)."""
            } else {
                """To feed text/knowledge files (.txt, .md, .json, .csv) into Rameses's memory for both chat and Gemini Live voice:
• Open the Side Menu (☰) ➔ 'Knowledge Base & File Upload (RAG)' ➔ tap 'Upload File'."""
            }
        )
    }
}

@Composable
private fun GuideStepCard(
    badge: String,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(RoyalGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge,
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PharaonicMidnight
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = RoyalGoldLight
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                fontFamily = AmiriFontFamily,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = TextPrimary.copy(alpha = 0.92f),
                textAlign = TextAlign.Start
            )
        }
    }
}

private fun copyGlbUriToInternalStorage(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        var displayName = "custom_avatar.glb"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                val queried = cursor.getString(nameIdx)
                if (!queried.isNullOrBlank()) {
                    displayName = queried
                }
            }
        }

        val modelsDir = File(context.filesDir, "custom_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").let {
            if (it.lowercase().endsWith(".glb") || it.lowercase().endsWith(".gltf")) it else "$it.glb"
        }
        val destFile = File(modelsDir, "rig_${System.currentTimeMillis()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        if (!destFile.exists() || destFile.length() < 16L) {
            destFile.delete()
            return null
        }

        // Verify glTF binary magic header ("glTF" = 0x46546C67) or valid JSON glTF
        val header = ByteArray(4)
        destFile.inputStream().use { it.read(header) }
        val magic = String(header, Charsets.US_ASCII)
        if (magic != "glTF" && !magic.trimStart().startsWith("{")) {
            destFile.delete()
            return null
        }

        Pair(destFile.absolutePath, displayName)
    } catch (_: Exception) {
        null
    }
}
