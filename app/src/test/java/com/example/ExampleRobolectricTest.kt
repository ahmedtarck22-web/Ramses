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

  @Test
  fun `SecureKeyStoreHelper encrypts secrets with AES-GCM and migrates legacy cleartext preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("test_secure_keystore_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    // Seed a simulated legacy cleartext key and verify automatic migration removes cleartext
    val sampleSecret = "AIzaSySimulatedUnitTestKey_1234567890"
    prefs.edit().putString("custom_api_key", sampleSecret).commit()

    val migratedRead = com.example.security.SecureKeyStoreHelper.readEncryptedPref(
      prefs = prefs,
      encryptedKey = "custom_api_key_enc_v1",
      legacyPlainKey = "custom_api_key"
    )
    assertEquals(sampleSecret, migratedRead)
    assertFalse("Legacy cleartext key must be deleted after migration", prefs.contains("custom_api_key"))

    val storedCipher = prefs.getString("custom_api_key_enc_v1", null).orEmpty()
    assertTrue("Encrypted payload must be non-empty iv:ciphertext", storedCipher.contains(":"))
    assertFalse("Stored ciphertext must never contain raw secret", storedCipher.contains(sampleSecret))

    // Subsequent read decrypts cleanly from ciphertext
    val secondRead = com.example.security.SecureKeyStoreHelper.readEncryptedPref(
      prefs = prefs,
      encryptedKey = "custom_api_key_enc_v1",
      legacyPlainKey = "custom_api_key"
    )
    assertEquals(sampleSecret, secondRead)
  }

  @Test
  fun `two-stage RAG retrieval returns accurate cited passages from bundled five books`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeDb = createLocalTestFirestore(context)
    val repo = FirestoreRagRepository(
      db = fakeDb,
      appContext = context,
      authProvider = { null }
    )

    val kadeshPassages = repo.retrieveRelevantPassages("ما تفاصيل معركة قادش ومعاهدة السلام مع الحيثيين؟")
    assertTrue("Expected relevant passages for Battle of Kadesh", kadeshPassages.isNotEmpty())
    val topCitationTitle = kadeshPassages.first().toCitationTitle()
    val topCitationUri = kadeshPassages.first().toCitationUri()
    assertTrue(topCitationTitle.contains("•"))
    assertTrue(topCitationUri.startsWith("firestore://rag_books/"))

    // Verify GeminiApiClient preserves exact RAG citations without duplicates
    val client = GeminiApiClient()
    val ragDocs = kadeshPassages.map { p ->
      com.example.data.model.RagDocument(
        id = "cloud_chunk_${p.chunk.chunkId}",
        title = p.toCitationTitle(),
        content = p.chunk.content,
        gcsUri = p.toCitationUri()
      )
    }
    val citations = client.buildRagContextCitations(ragDocs + ragDocs)
    assertEquals(kadeshPassages.size, citations.size)
    assertEquals(topCitationTitle, citations.first().title)
    assertEquals(topCitationUri, citations.first().uri)
  }

  @Test
  fun `multimodal image request downsamples oversized bitmap and encodes inlineData JPEG`() {
    val client = GeminiApiClient()
    val oversizedBitmap = android.graphics.Bitmap.createBitmap(
      2048,
      1536,
      android.graphics.Bitmap.Config.ARGB_8888
    )
    val bodyJson = JSONObject(
      client.buildRequestBody(
        prompt = "حلل هذا النقش الهيروغليفي",
        history = emptyList(),
        imageBitmap = oversizedBitmap,
        withSearch = false,
        isVoiceCall = false,
        ragContext = emptyList(),
        appLanguage = AppLanguage.ARABIC,
        gemPersona = "RAMSES"
      )
    )
    val parts = bodyJson.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
    var foundInlineJpeg = false
    for (i in 0 until parts.length()) {
      val inline = parts.getJSONObject(i).optJSONObject("inlineData")
      if (inline != null) {
        assertEquals("image/jpeg", inline.getString("mimeType"))
        assertTrue(inline.getString("data").length > 32)
        foundInlineJpeg = true
      }
    }
    assertTrue("Expected inlineData JPEG part in multimodal request", foundInlineJpeg)
  }

  @Test
  fun `all bundled 3D GLB avatar and artifact models have valid glTF 2 binary headers and lengths`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val glbPaths = listOf(
      "models/Ramses.final.glb",
      "models/Ramses.original.glb"
    ) + com.example.data.model.RamsesArtifact.ALL_ARTIFACTS.map { it.modelPath }

    assertEquals(7, glbPaths.distinct().size)

    for (assetPath in glbPaths.distinct()) {
      val bytes = context.assets.open(assetPath).use { it.readBytes() }
      assertTrue("GLB asset $assetPath is unexpectedly tiny", bytes.size > 128)
      val header = java.nio.ByteBuffer.wrap(bytes, 0, 12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
      val magic = header.int
      val version = header.int
      val declaredLength = header.int
      assertEquals("Invalid glTF magic in $assetPath", 0x46546C67, magic)
      assertEquals("Expected glTF version 2 in $assetPath", 2, version)
      assertEquals("Declared GLB byte length mismatch in $assetPath", bytes.size, declaredLength)
    }
  }

  private fun createLocalTestFirestore(context: Context): com.google.firebase.firestore.FirebaseFirestore {
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(
        context,
        com.google.firebase.FirebaseOptions.Builder()
          .setApplicationId("com.aistudio.ramsesai.kqzmpw")
          .setProjectId("demo-no-project")
          .setApiKey("fake-api-key-for-unit-test")
          .build()
      )
    }
    return com.google.firebase.firestore.FirebaseFirestore.getInstance(
      com.google.firebase.FirebaseApp.getInstance(),
      "demo-unit-db"
    )
  }
}

