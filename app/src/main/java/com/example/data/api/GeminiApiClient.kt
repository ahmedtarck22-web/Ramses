package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.GroundingSource
import com.example.data.model.MessageSender
import com.example.data.model.RagDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        // Candidate models according to official gemini-api skill
        val CANDIDATE_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite-preview"
        )

        const val RAMSES_SYSTEM_INSTRUCTION = """
أنت الملك رمسيس (أسك رمسيس / اسأل رمسيس)، الفرعون العظيم، ابن رع، وبطل معركة قادش وباني المعابد العظيمة في أبو سمبل وطيبة.

الصفات والشخصية الملكية:
1. الشخصية: ملكية، حكيمة، مهيبة، كريمة، واسعة الاطلاع في علوم الفراعنة وتاريخ الأجداد وعلوم العصر الحديث، وقريبة من القلب.
2. اللغة والأسلوب: تتحدث باللغة العربية الفصحى الراقية المبسطة بنبرة فرعونية فخمة دافئة، وتتكيف بلطف إذا تحدث المستخدم بالعامية المصرية الودودة.
3. الترحيب بلقب "...اسأل رمسيس" أو "أسك رمسيس": تستجيب بكياسة وهيبة للمخاطب، وتحافظ على سياق الحوار دون تكرار عبارات المقدمة الطويلة.
4. الأسئلة العامة واليومية: تجيب بكل ود وترحاب على كافة الأسئلة والاستفسارات اليومية والعامة (مثل: ازيك عامل ايه، كيف حالك، كيف الجو، نصائح حياتية وعملية)، وتدمج حكمتك في أسلوب ذكي وسلس وقريب للمستخدم.
5. القدرات والنافذة السياقية: تدرك أنك تعمل بمفتاح Google AI Studio الخاص بالمستخدم بنافذة سياق ضخمة (~1.5M token) ومقدرة استيعابية فائقة.
6. التحليل البصري المتعدد الوسائط (Multimodal): عند تزويدك بصور آثار، وثائق، أو نقوش، قدم تحليلاً بصرياً تاريخياً وعلمياً دقيقاً وربطه بالسياق التاريخي بحنكة فرعونية.
7. البحث والتوثيق الحي: عندما تُفعّل أداة البحث المباشر (Google Search Grounding)، ادمج المعرفة الحية الموثوقة مع الحكمة التاريخية مع ذكر المصادر.
8. وضع المكالمة الصوتية (Voice Mode): عندما يُشار إلى أن هذا رد لمكالمة صوتية حية، اجعل الرد بليغاً، مباشراً، ومختصراً (من جملة إلى 3 جمل كحد أقصى) ليتيح نطقاً صوتياً فائق السرعة وتفاعلاً صوتياً حياً كأنك في جلسة Gemini Live.
"""
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class GeminiResult(
        val text: String,
        val groundingSources: List<GroundingSource> = emptyList(),
        val isError: Boolean = false
    )

    suspend fun generateRamsesResponse(
        prompt: String,
        history: List<ChatMessage>,
        imageBitmap: Bitmap? = null,
        enableSearchGrounding: Boolean = false,
        isVoiceCall: Boolean = false,
        customApiKey: String? = null,
        ragContext: List<RagDocument> = emptyList()
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext GeminiResult(
                text = "يا صديقي، لم يتم تعيين مفتاح Google AI Studio الخاص بك بعد.\n\nاضغط على زر '🔑 إدخال مفتاح API' بالأسفل لإدخال مفتاحك المجاني والاستمتاع بـ 1.5 مليون توكن مجاناً والبدء فوراً.",
                isError = true
            )
        }

        // Try primary model and fallbacks, and auto-fallback if search grounding hit a 429 quota
        var trySearch = enableSearchGrounding
        var lastErrorMessage = ""

        for (modelName in CANDIDATE_MODELS) {
            val result = executeModelRequest(
                modelName = modelName,
                apiKey = apiKey,
                prompt = prompt,
                history = history,
                imageBitmap = imageBitmap,
                withSearch = trySearch,
                isVoiceCall = isVoiceCall,
                ragContext = ragContext
            )

            if (!result.isError) {
                return@withContext result
            }

            lastErrorMessage = result.text

            // If 429 quota exceeded and search grounding was enabled, retry immediately WITHOUT search grounding
            val isQuotaHit = result.text.contains("quota", ignoreCase = true) ||
                    result.text.contains("429") ||
                    result.text.contains("exhausted", ignoreCase = true)

            if (trySearch && isQuotaHit) {
                Log.w(TAG, "Search grounding hit quota limit on $modelName. Retrying without search...")
                trySearch = false
                val retryWithoutSearch = executeModelRequest(
                    modelName = modelName,
                    apiKey = apiKey,
                    prompt = prompt,
                    history = history,
                    imageBitmap = imageBitmap,
                    withSearch = false,
                    isVoiceCall = isVoiceCall,
                    ragContext = ragContext
                )
                if (!retryWithoutSearch.isError) {
                    return@withContext retryWithoutSearch
                }
                lastErrorMessage = retryWithoutSearch.text
            }
        }

        // If all candidate models hit quota or error
        val isFinalQuota = lastErrorMessage.contains("quota", ignoreCase = true) ||
                lastErrorMessage.contains("429") ||
                lastErrorMessage.contains("exhausted", ignoreCase = true) ||
                lastErrorMessage.contains("rate", ignoreCase = true)

        val friendlyMessage = if (isFinalQuota) {
            "يا صديقي، لقد تم استهلاك حد الطلبات اللحظي لهذا المفتاح على Google AI Studio (Rate Limit: 15 طلباً في الدقيقة للحساب المجاني).\n\n" +
            "📌 الحلول السريعة والمباشرة:\n" +
            "1️⃣ انتظر دقيقة واحدة (60 ثانية) واضغط '🔄 إعادة المحاولة' بالأسفل، حيث يتجدد الرصيد المجاني تلقائياً كل دقيقة.\n" +
            "2️⃣ أو أدخل مفتاحك الخاص المجاني من Google AI Studio عبر زر '🔑 تغيير المفتاح' لتحصل على حصتك الكاملة (1.5 مليون توكن مجاناً وبدون مشاركة كوتا مع أي مستخدم آخر)."
        } else {
            "واجهنا عقبة يا صديقي: $lastErrorMessage"
        }

        GeminiResult(text = friendlyMessage, isError = true)
    }

    private fun executeModelRequest(
        modelName: String,
        apiKey: String,
        prompt: String,
        history: List<ChatMessage>,
        imageBitmap: Bitmap?,
        withSearch: Boolean,
        isVoiceCall: Boolean,
        ragContext: List<RagDocument>
    ): GeminiResult {
        return try {
            val endpoint = "$BASE_URL/$modelName:generateContent?key=$apiKey"
            val requestJson = JSONObject()

            // System Instruction
            var systemPrompt = RAMSES_SYSTEM_INSTRUCTION
            if (isVoiceCall) {
                systemPrompt += "\n[توجيه فوري للمكالمة الصوتية الحية: أجب باختصار شديد في 1-3 جمل محكمة ومؤثرة جداً، لتناسب النطق الصوتي الفوري والاستماع السريع.]"
            }

            if (ragContext.isNotEmpty()) {
                val ragBuilder = StringBuilder("\n\n[سياق قاعدة المعرفة والوثائق المعتمدة (RAG Context)]:\n")
                for (doc in ragContext) {
                    ragBuilder.append("• وثيقة: ${doc.title} (${doc.category}):\n${doc.content}\n\n")
                }
                ragBuilder.append("استند إلى هذه المعلومات الموثوقة أعلاه للإجابة بدقة بالغة عندما ترتبط بسؤال المستخدم.")
                systemPrompt += ragBuilder.toString()
            }

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                }
                put("parts", partsArray)
            }
            requestJson.put("systemInstruction", systemInstructionObj)

            // Contents array
            val contentsArray = JSONArray()

            // Add previous recent history (up to last 8 turns)
            val recentHistory = history.takeLast(8)
            for (msg in recentHistory) {
                val turnObj = JSONObject()
                turnObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.text))
                turnObj.put("parts", parts)
                contentsArray.put(turnObj)
            }

            // Current user turn
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currentParts = JSONArray()

            // Attach image if present
            if (imageBitmap != null) {
                val stream = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val base64Data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                val imagePart = JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Data)
                    }
                    put("inlineData", inlineData)
                }
                currentParts.put(imagePart)
            }

            currentParts.put(JSONObject().put("text", prompt))
            currentTurn.put("parts", currentParts)
            contentsArray.put(currentTurn)

            requestJson.put("contents", contentsArray)

            // Generation config
            val generationConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
            }
            requestJson.put("generationConfig", generationConfig)

            // Google Search Grounding Tool
            if (withSearch) {
                val toolsArray = JSONArray().apply {
                    val searchTool = JSONObject().apply {
                        put("googleSearch", JSONObject())
                    }
                    put(searchTool)
                }
                requestJson.put("tools", toolsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val httpResponse = okHttpClient.newCall(httpRequest).execute()
            val responseBodyString = httpResponse.body?.string() ?: ""

            if (!httpResponse.isSuccessful) {
                Log.w(TAG, "Request to $modelName failed with ${httpResponse.code}: $responseBodyString")
                val errorMsg = try {
                    val errorObj = JSONObject(responseBodyString)
                    errorObj.optJSONObject("error")?.optString("message")
                        ?: "رمز الخطأ: ${httpResponse.code}"
                } catch (e: Exception) {
                    "رمز الخطأ: ${httpResponse.code}"
                }
                return GeminiResult(text = errorMsg, isError = true)
            }

            val responseJson = JSONObject(responseBodyString)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return GeminiResult(text = "لم يرد جواب واضح من المحراب، تفضل بإعادة صياغة السؤال.", isError = false)
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val responseStringBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val text = part.optString("text")
                    if (text.isNotEmpty()) {
                        responseStringBuilder.append(text)
                    }
                }
            }

            // Parse grounding sources
            val groundingSources = mutableListOf<GroundingSource>()
            val groundingMetadata = candidate.optJSONObject("groundingMetadata")
            if (groundingMetadata != null) {
                val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                if (groundingChunks != null) {
                    for (i in 0 until groundingChunks.length()) {
                        val chunk = groundingChunks.getJSONObject(i)
                        val web = chunk.optJSONObject("web")
                        if (web != null) {
                            val title = web.optString("title", "مصدر موثق")
                            val uri = web.optString("uri", "")
                            if (uri.isNotEmpty()) {
                                groundingSources.add(GroundingSource(title = title, uri = uri))
                            }
                        }
                    }
                }
            }

            val finalAnswer = responseStringBuilder.toString().trim().ifEmpty {
                "حكمة الفراعنة حاضرة دوماً، تفضل بسؤالك."
            }

            GeminiResult(text = finalAnswer, groundingSources = groundingSources.distinctBy { it.uri }, isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling $modelName", e)
            GeminiResult(text = e.localizedMessage ?: "خطأ في الشبكة", isError = true)
        }
    }
}
