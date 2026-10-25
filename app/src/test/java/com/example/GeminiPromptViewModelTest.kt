package com.example

import com.example.ui.viewmodel.GeminiPromptUiState
import com.example.ui.viewmodel.GeminiPromptViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiPromptViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testEmptyPromptRejection() {
        val viewModel = GeminiPromptViewModel()
        viewModel.sendPrompt("   ")
        val state = viewModel.uiState.value
        assertTrue(state is GeminiPromptUiState.Error)
        assertEquals("Prompt cannot be empty.", (state as GeminiPromptUiState.Error).message)
    }

    @Test
    fun testUpdatePromptInput() {
        val viewModel = GeminiPromptViewModel()
        viewModel.updatePromptInput("Tell me about King Ramses II")
        assertEquals("Tell me about King Ramses II", viewModel.promptInput.value)
    }

    @Test
    fun testSelectModel() {
        val viewModel = GeminiPromptViewModel()
        viewModel.selectModel("gemini-3.8-flash")
        assertEquals("gemini-3.8-flash", viewModel.selectedModel.value)
    }

    @Test
    fun testClearStateAndHistory() {
        val viewModel = GeminiPromptViewModel()
        viewModel.sendPrompt("")
        assertTrue(viewModel.uiState.value is GeminiPromptUiState.Error)
        viewModel.clearState()
        assertEquals(GeminiPromptUiState.Idle, viewModel.uiState.value)
        viewModel.clearHistory()
        assertTrue(viewModel.promptHistory.value.isEmpty())
    }

    @Test
    fun testMockNetworkSuccessResponse() = runTest {
        val mockResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Ramses II was the third pharaoh of the Nineteenth Dynasty of Egypt."
                      }
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()

        var capturedHeaderKey: String? = null
        var capturedUrlQueryKey: String? = "UNSET"

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedHeaderKey = req.header("x-goog-api-key")
                capturedUrlQueryKey = req.url.queryParameter("key")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockResponseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val viewModel = object : GeminiPromptViewModel(mockClient, testDispatcher) {
            // Override to provide a guaranteed test key for mock test
            override fun getApiKeyFromBuildConfig(): String {
                val realKey = super.getApiKeyFromBuildConfig()
                return realKey.ifEmpty { "TEST_MOCK_GEMINI_KEY" }
            }
        }

        viewModel.sendPrompt("Who was Ramses II?")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, but was $state", state is GeminiPromptUiState.Success)
        val success = state as GeminiPromptUiState.Success
        assertEquals("Who was Ramses II?", success.prompt)
        assertTrue(success.responseText.contains("Nineteenth Dynasty"))
        assertEquals(1, viewModel.promptHistory.value.size)
        assertNull("API key must NEVER be passed as a URL query parameter", capturedUrlQueryKey)
        assertTrue(!capturedHeaderKey.isNullOrBlank())
    }

    @Test
    fun testMockNetworkErrorResponse() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 400,
                "message": "API key not valid. Please pass a valid API key.",
                "status": "INVALID_ARGUMENT"
              }
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(errorJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val viewModel = object : GeminiPromptViewModel(mockClient, testDispatcher) {
            override fun getApiKeyFromBuildConfig(): String = "INVALID_KEY"
        }

        viewModel.sendPrompt("Test error")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, but was $state", state is GeminiPromptUiState.Error)
        val error = state as GeminiPromptUiState.Error
        assertTrue(error.message.contains("API key not valid"))
    }
}
