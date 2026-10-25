package com.example

import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testRamsesModelConfiguration() {
        val models = com.example.data.api.GeminiApiClient.CANDIDATE_MODELS
        assertTrue(models.contains("gemini-3.1-flash-lite"))
        assertTrue(models.contains("gemini-3.5-flash-lite"))
        assertTrue(models.contains("gemini-3.8-flash"))
        assertTrue(models.contains("gemini-3-flash-preview"))
        // Verify retired legacy models are not in primary candidate list
        org.junit.Assert.assertFalse(models.contains("gemini-2.5-flash"))
    }

    @Test
    fun testBilingualStrings() {
        assertEquals("اسأل رمسيس", AppStrings.appName(AppLanguage.ARABIC))
        assertEquals("Ask Rameses", AppStrings.appName(AppLanguage.ENGLISH))
        assertEquals("...اسأل رمسيس عن أي شيء", AppStrings.inputPlaceholder(AppLanguage.ARABIC))
        assertEquals("Ask Rameses anything...", AppStrings.inputPlaceholder(AppLanguage.ENGLISH))
    }

    @Test
    fun testLiveCallStatusBilingual() {
        assertTrue(AppStrings.liveStatusListening(AppLanguage.ARABIC).contains("يستمع"))
        assertTrue(AppStrings.liveStatusListening(AppLanguage.ENGLISH).contains("Listening"))
    }

    @Test
    fun test3DArtifactsModelConfiguration() {
        val artifacts = com.example.data.model.RamsesArtifact.ALL_ARTIFACTS
        assertEquals(5, artifacts.size)
        assertTrue(artifacts.any { it.id == "ramses_statue" })
        assertTrue(artifacts.any { it.id == "great_pyramid" })
        assertTrue(artifacts.any { it.id == "golden_mask" })
        assertTrue(artifacts.any { it.id == "sacred_scarab" })
        assertTrue(artifacts.any { it.id == "eye_of_horus" })
        artifacts.forEach { artifact ->
            assertTrue(artifact.modelPath.endsWith(".glb"))
            assertTrue(artifact.titleAr.isNotBlank())
            assertTrue(artifact.titleEn.isNotBlank())
        }
    }

    @Test
    fun testVoiceCallTerminologySeparation() {
        val voiceCallTitleAr = AppStrings.drawerVoiceCall(AppLanguage.ARABIC)
        val voiceCallTitleEn = AppStrings.drawerVoiceCall(AppLanguage.ENGLISH)
        // Verify not labeled as Google Gemini Live API
        org.junit.Assert.assertFalse(voiceCallTitleAr.contains("Gemini Live"))
        org.junit.Assert.assertFalse(voiceCallTitleEn.contains("Gemini Live"))
    }

    @Test
    fun testChatMessageOrderAndTimestamp() {
        val t1 = System.currentTimeMillis()
        val t2 = t1 + 100
        val m1 = com.example.data.model.ChatMessageEntity(
            sessionId = "session_test",
            sender = "USER",
            text = "Hello Ramses",
            timestamp = t1
        )
        val m2 = com.example.data.model.ChatMessageEntity(
            sessionId = "session_test",
            sender = "RAMSES",
            text = "Greetings mortal",
            timestamp = t2
        )
        assertTrue(m1.timestamp < m2.timestamp)
        assertEquals("USER", m1.sender)
        assertEquals("RAMSES", m2.sender)
    }

    @Test
    fun testFastModelResolutionAndLiveModels() {
        assertEquals(
            "gemini-3.1-flash-lite",
            com.example.data.api.GeminiApiClient.resolveActiveModelName("gemini-2.0-flash")
        )
        assertEquals(
            "gemini-3.8-flash",
            com.example.data.api.GeminiApiClient.resolveActiveModelName("gemini-3.8-flash")
        )
        assertTrue(
            com.example.service.GeminiLiveWebSocketClient.LIVE_CANDIDATE_MODELS.contains(
                "models/gemini-3.1-flash-live-preview"
            )
        )
    }

    @Test
    fun testAvatarRigConfigSerialization() {
        val original = com.example.data.model.AvatarRigConfig(
            customModelPath = "/data/user/0/com.example/files/custom_models/pharaoh.glb",
            customModelName = "pharaoh.glb",
            showSkeletonLinesOnAvatar = true,
            lipSyncIntensity = 1.35f,
            modelRotationY = 0f,
            modelScale = 0.72f
        )
        val json = original.toJsonString()
        val restored = com.example.data.model.AvatarRigConfig.fromJsonString(json)
        assertEquals("pharaoh.glb", restored.customModelName)
        assertTrue(restored.showSkeletonLinesOnAvatar)
        assertEquals(0f, restored.modelRotationY, 0.01f)
        assertEquals(0.72f, restored.modelScale, 0.01f)
        assertEquals(11, restored.joints.size)
        assertTrue(com.example.data.model.AvatarRigConfig.BONE_CONNECTIONS.isNotEmpty())
    }

    @Test
    fun testSecureKeyStoreHelperRedaction() {
        val fakeKey = "AIzaSyTestSecretKey987654321"
        val rawLog = "HTTP 400 error while calling endpoint with key=$fakeKey on host"
        val sanitized = com.example.security.SecureKeyStoreHelper.redactSecret(rawLog, fakeKey)
        org.junit.Assert.assertFalse(sanitized.contains(fakeKey))
        assertTrue(sanitized.contains("[REDACTED_KEY]"))
    }

    @Test
    fun testArabicMorphologicalStemmingAndNormalization() {
        val stems1 = com.example.data.repository.FirestoreRagRepository.extractSearchStems("والمعاهدات الملكية")
        val stems2 = com.example.data.repository.FirestoreRagRepository.extractSearchStems("معاهدة ملكي")
        assertTrue("Expected morphological stem overlap between plural/clitic and singular forms", stems1.intersect(stems2).isNotEmpty())

        val norm = com.example.data.repository.FirestoreRagRepository.normalizeSearchText("إِبْرَاهِيمُ وَالْآثَارُ الْمِصْرِيَّةُ")
        assertEquals("ابراهيم والاثار المصريه", norm)
    }
}

