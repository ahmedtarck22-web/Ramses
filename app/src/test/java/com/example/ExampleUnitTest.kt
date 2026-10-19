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
        assertTrue(models.contains("gemini-3.8-flash"))
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
}
