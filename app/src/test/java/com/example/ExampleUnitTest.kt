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
        assertTrue(models.contains("gemini-2.5-flash"))
        assertTrue(models.contains("gemini-3.5-flash"))
        assertTrue(models.contains("gemini-flash-latest"))
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
}
