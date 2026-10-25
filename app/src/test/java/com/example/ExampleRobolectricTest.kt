package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.GeminiApiClient
import com.example.data.model.AppLanguage
import com.example.data.repository.FirestoreRagRepository
import com.example.service.GeminiLiveWebSocketClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ask Rameses", appName)
    assertTrue(context.getString(R.string.scroll_to_bottom_desc).isNotBlank())
    assertTrue(context.getString(R.string.live_echo_guard_ar).isNotBlank())
    assertTrue(context.getString(R.string.live_echo_guard_en).isNotBlank())
  }

  @Test
  fun `bundled five books RAG pack loads all books folders and chunks without error`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bundles = FirestoreRagRepository.loadBundledFiveBooks(context)
    assertEquals(5, bundles.size)
    val totalFolders = bundles.sumOf { it.folders.size }
    val totalChunks = bundles.sumOf { it.chunks.size }
    assertEquals(39, totalFolders)
    assertEquals(278, totalChunks)
    assertTrue(bundles.all { it.book.title.isNotBlank() })
  }

  @Test
  fun `written text chat enforces 8192 maxOutputTokens and multi-paragraph directive while voice stays concise`() {
    val client = GeminiApiClient()

    val writtenBodyJson = JSONObject(
      client.buildRequestBody(
        prompt = "حدثني عن معبد أبو سمبل",
        history = emptyList(),
        imageBitmap = null,
        withSearch = false,
        isVoiceCall = false,
        ragContext = emptyList(),
        appLanguage = AppLanguage.ARABIC,
        gemPersona = "RAMSES"
      )
    )
    val writtenGenConfig = writtenBodyJson.getJSONObject("generationConfig")
    assertEquals(8192, writtenGenConfig.getInt("maxOutputTokens"))

    val systemText = writtenBodyJson
      .getJSONObject("systemInstruction")
      .getJSONArray("parts")
      .getJSONObject(0)
      .getString("text")
    assertTrue(systemText.contains("توجيه أساسي وملزم للردود النصية المكتوبة"))

    val voiceBodyJson = JSONObject(
      client.buildRequestBody(
        prompt = "حدثني عن معبد أبو سمبل",
        history = emptyList(),
        imageBitmap = null,
        withSearch = false,
        isVoiceCall = true,
        ragContext = emptyList(),
        appLanguage = AppLanguage.ARABIC,
        gemPersona = "RAMSES"
      )
    )
    val voiceGenConfig = voiceBodyJson.getJSONObject("generationConfig")
    assertEquals(220, voiceGenConfig.getInt("maxOutputTokens"))
  }

  @Test
  fun `live voice acoustic echo gate blocks mic feedback during AI speech and echo tail then clears on manual interrupt`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val liveClient = GeminiLiveWebSocketClient(context)

    assertFalse(liveClient.isEchoGateActive(nowElapsedMs = 1_000L))

    // Active AI playback gates microphone echo
    liveClient.setEchoGateForTesting(aiPlaying = true, echoTailUntilMs = 0L)
    assertTrue(liveClient.isEchoGateActive(nowElapsedMs = 1_000L))

    // Post-playback reverb tail keeps gate active until cooldown elapses
    liveClient.setEchoGateForTesting(aiPlaying = false, echoTailUntilMs = 2_550L)
    assertTrue(liveClient.isEchoGateActive(nowElapsedMs = 2_200L))
    assertFalse(liveClient.isEchoGateActive(nowElapsedMs = 2_600L))

    // Manual interrupt button immediately clears echo gate so user can speak right away
    liveClient.setEchoGateForTesting(aiPlaying = true, echoTailUntilMs = 9_999L)
    liveClient.interruptModelSpeech()
    assertFalse(liveClient.isEchoGateActive(nowElapsedMs = 3_000L))
  }
}

