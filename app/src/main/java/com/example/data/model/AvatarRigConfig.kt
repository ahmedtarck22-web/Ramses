package com.example.data.model

/**
 * Represents a single anatomical joint node on the 3D Avatar Rig.
 * Coordinates (x, y) are normalized in [0f..1f] relative to the avatar viewport.
 */
data class RigJoint(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val x: Float,
    val y: Float
)

/**
 * Complete configuration for custom .glb avatar loading and interactive Rig skeleton lines.
 * Uses pure-Kotlin serialization so it works identically on Android runtime and pure JVM tests.
 */
data class AvatarRigConfig(
    val customModelPath: String? = null,
    val customModelName: String? = null,
    val joints: List<RigJoint> = defaultJoints(),
    val showSkeletonLinesOnAvatar: Boolean = false,
    val lipSyncIntensity: Float = 1.0f,
    val gestureIntensity: Float = 1.0f,
    val modelRotationY: Float = 90f,
    val modelScale: Float = 0.54f
) {
    fun findJoint(id: String): RigJoint? = joints.firstOrNull { it.id == id }

    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("v=1\n")
        sb.append("customModelPath=").append(escapeValue(customModelPath.orEmpty())).append('\n')
        sb.append("customModelName=").append(escapeValue(customModelName.orEmpty())).append('\n')
        sb.append("showSkeletonLinesOnAvatar=").append(showSkeletonLinesOnAvatar).append('\n')
        sb.append("lipSyncIntensity=").append(lipSyncIntensity).append('\n')
        sb.append("gestureIntensity=").append(gestureIntensity).append('\n')
        sb.append("modelRotationY=").append(modelRotationY).append('\n')
        sb.append("modelScale=").append(modelScale).append('\n')
        for (j in joints) {
            sb.append("joint=")
                .append(escapeValue(j.id)).append('|')
                .append(j.x).append('|')
                .append(j.y).append('\n')
        }
        return sb.toString()
    }

    companion object {
        /**
         * Pairs of joint IDs forming the connected rig bone lines ("خطوط الـ Rig").
         */
        val BONE_CONNECTIONS: List<Pair<String, String>> = listOf(
            // Central head, jaw & spine chain
            "head_top" to "jaw_mouth",
            "jaw_mouth" to "neck",
            "neck" to "chest_spine",
            "chest_spine" to "pelvis_root",
            // Right arm chain
            "neck" to "shoulder_r",
            "shoulder_r" to "elbow_r",
            "elbow_r" to "hand_r",
            // Left arm chain
            "neck" to "shoulder_l",
            "shoulder_l" to "elbow_l",
            "elbow_l" to "hand_l",
            // Clavicle / shoulder bridge line
            "shoulder_l" to "shoulder_r"
        )

        fun defaultJoints(): List<RigJoint> = listOf(
            RigJoint("head_top", "قمة التاج / الرأس", "Head Crown", 0.50f, 0.20f),
            RigJoint("jaw_mouth", "الفك والفم (النطق)", "Jaw / Mouth", 0.50f, 0.34f),
            RigJoint("neck", "الرقبة", "Neck", 0.50f, 0.41f),
            RigJoint("chest_spine", "الصدر / العمود الفقري", "Chest / Spine", 0.50f, 0.53f),
            RigJoint("pelvis_root", "الحوض (نقطة الارتكاز)", "Pelvis Root", 0.50f, 0.69f),
            RigJoint("shoulder_r", "الكتف الأيمن", "Right Shoulder", 0.37f, 0.44f),
            RigJoint("elbow_r", "الكوع الأيمن", "Right Elbow", 0.32f, 0.56f),
            RigJoint("hand_r", "اليد اليمنى", "Right Hand", 0.39f, 0.64f),
            RigJoint("shoulder_l", "الكتف الأيسر", "Left Shoulder", 0.63f, 0.44f),
            RigJoint("elbow_l", "الكوع الأيسر", "Left Elbow", 0.68f, 0.56f),
            RigJoint("hand_l", "اليد اليسرى", "Left Hand", 0.61f, 0.64f)
        )

        private fun escapeValue(raw: String): String =
            raw.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p")

        private fun unescapeValue(escaped: String): String =
            escaped.replace("\\p", "|").replace("\\n", "\n").replace("\\\\", "\\")

        fun fromJsonString(serialized: String?): AvatarRigConfig {
            if (serialized.isNullOrBlank()) return AvatarRigConfig()
            return try {
                val defaults = defaultJoints().associateBy { it.id }
                val updatedCoords = mutableMapOf<String, Pair<Float, Float>>()
                var customPath: String? = null
                var customName: String? = null
                var showLines = false
                var lipSync = 1.0f
                var gesture = 1.0f
                var rotY = 90f
                var scale = 0.54f

                for (line in serialized.lineSequence()) {
                    val idx = line.indexOf('=')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1)
                    when (key) {
                        "customModelPath" -> customPath = unescapeValue(value).takeIf { it.isNotBlank() }
                        "customModelName" -> customName = unescapeValue(value).takeIf { it.isNotBlank() }
                        "showSkeletonLinesOnAvatar" -> showLines = value.toBooleanStrictOrNull() ?: false
                        "lipSyncIntensity" -> lipSync = (value.toFloatOrNull() ?: 1.0f).coerceIn(0.2f, 2.0f)
                        "gestureIntensity" -> gesture = (value.toFloatOrNull() ?: 1.0f).coerceIn(0.0f, 2.0f)
                        "modelRotationY" -> rotY = (value.toFloatOrNull() ?: 90f).coerceIn(-180f, 180f)
                        "modelScale" -> scale = (value.toFloatOrNull() ?: 0.54f).coerceIn(0.15f, 1.6f)
                        "joint" -> {
                            val parts = value.split('|')
                            if (parts.size == 3) {
                                val id = unescapeValue(parts[0])
                                val x = (parts[1].toFloatOrNull() ?: 0.5f).coerceIn(0.05f, 0.95f)
                                val y = (parts[2].toFloatOrNull() ?: 0.5f).coerceIn(0.05f, 0.95f)
                                if (defaults.containsKey(id)) {
                                    updatedCoords[id] = x to y
                                }
                            }
                        }
                    }
                }

                val mergedJoints = defaultJoints().map { def ->
                    val override = updatedCoords[def.id]
                    if (override != null) def.copy(x = override.first, y = override.second) else def
                }

                AvatarRigConfig(
                    customModelPath = customPath,
                    customModelName = customName,
                    joints = mergedJoints,
                    showSkeletonLinesOnAvatar = showLines,
                    lipSyncIntensity = lipSync,
                    gestureIntensity = gesture,
                    modelRotationY = rotY,
                    modelScale = scale
                )
            } catch (_: Exception) {
                AvatarRigConfig()
            }
        }
    }
}
