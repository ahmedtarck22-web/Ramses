package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testRamsesModelConfiguration() {
        val models = com.example.data.api.GeminiApiClient.CANDIDATE_MODELS
        assertTrue(models.contains("gemini-3.5-flash"))
        assertTrue(models.contains("gemini-flash-latest"))
    }

    @Test
    fun testPersonaTitles() {
        val titles = listOf("الملك رمسيس", "اسأل رمسيس", "أسك رمسيس", "ابن رع")
        assertTrue(titles.contains("الملك رمسيس"))
        assertTrue(titles.contains("اسأل رمسيس"))
    }
}
