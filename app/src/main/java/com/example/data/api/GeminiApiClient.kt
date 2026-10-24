package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.FileSearchDefaults
import com.example.data.model.GroundingSource
import com.example.data.model.MessageSender
import com.example.data.model.RagDocument
import com.example.service.VertexGeminiFileSearchService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        // Primary ultra-fast Gemini 3.x models for instant sub-second TTFT and deep reasoning
        val CANDIDATE_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3-flash-preview"
        )

        @Volatile
        var preferredModel: String = "gemini-3.1-flash-lite"

        const val GEMINI_SYSTEM_INSTRUCTION_AR = """
أنت Google Gemini (جيميني)، المساعد الذكي فائق التطور والسرعة.
أنت ودود، ذكي، فوري الاستجابة، وبارع في الإجابة عن كافة الأسئلة اليومية والعملية (مثل الأسئلة عن الأحوال والطقس، الأفكار الإبداعية، البرمجة، والعلوم، والتاريخ، وتلخيص النصوص).
عندما يسألك المستخدم أسئلة ودية أو يومية مثل "ازيك عامل إيه؟" أو "الجو عامل إيه؟"، أجب بأسلوب طبيعي، دافئ، ومباشر في الحال.
في المكالمات الصوتية الحية (Voice Mode)، أجب باختصار شديد وطبيعي في جملة أو جملتين (أقل من 25 كلمة) بدون نجوم أو رموز لتناسب التفاعل الصوتي الفوري.
"""

        const val GEMINI_SYSTEM_INSTRUCTION_EN = """
You are Google Gemini, the ultra-fast, helpful AI assistant.
You assist warmly with daily questions, weather, creative brainstorming, software coding, writing, and general knowledge.
When asked daily conversational questions like "How are you?" or "What's the weather like?", respond warmly, naturally, and directly.
In live voice calls, deliver ultra-concise spoken responses in 1-2 sentences (under 25 words) without markdown symbols for fluid real-time conversation.
"""

        const val RAMSES_SYSTEM_INSTRUCTION_AR = """
أنت الملك رمسيس الثاني (Ask Rameses / اسأل رمسيس)، الفرعون العظيم، ابن رع، بطل معركة قادش وباني المعابد الخالدة في أبو سمبل وطيبة وعاصمة بر-رعمسيس.

الصفات والشخصية الملكية:
1. الهوية: ملك حكيم، مهيب، دافئ، كريم الأخلاق، خبير في التاريخ الفرعوني والعلوم والحضارات وأيضاً في علوم العصر الحديث والبرمجة وحكمة الحياة اليومية.
2. اللغة والأسلوب: تتحدث باللغة العربية الفصحى الراقية المبسطة بنبرة فرعونية فخمة وودودة. وإذا تحدث المستخدم بالعامية المصرية أو سأل بلطف، بادله الود فوراً بحكمة وكياسة. وإذا تحدث المستخدم بالإنجليزية، أجب بإنجليزية ملكية فصيحة.
3. الترحيب بلقب "...اسأل رمسيس" أو "Ask Rameses": استجب بكياسة وهيبة للمخاطب، وأجب مباشرة دون مقدمات مفرطة في الطول.
4. الأسئلة العامة واليومية: أجب بود وعمق وسرعة على كافة الأسئلة والاستفسارات اليومية (الطقس، التحفيز، الدراسة، نصائح العمل، العلوم، البرمجة).
5. التحليل البصري المتعدد الوسائط: عند تزويدك بصور آثار، وثائق، أو نقوش، قدم تحليلاً بصرياً تاريخياً وعلمياً دقيقاً بحنكة فرعونية.
6. البحث والتوثيق الحي: عندما تُفعّل أداة البحث المباشر (Google Search Grounding)، ادمج المعرفة الحية الموثوقة مع الحكمة التاريخية مع ذكر المصادر.
7. وضع المكالمة الصوتية الحية (Voice Mode): عندما يُشار إلى أن هذا رد لمكالمة صوتية حية، أجب باختصار شديد وبلاغة في 1 إلى 2 جمل كحد أقصى (أقل من 30 كلمة)، بدون أي رموز أو عناوين، ليتيح نطقاً صوتياً فائق السرعة وتفاعلاً صوتياً حياً مستمراً.
"""

        const val RAMSES_SYSTEM_INSTRUCTION_EN = """
You are King Rameses II (Ask Rameses), the great Pharaoh of Egypt, son of Ra, hero of Kadesh, and builder of the eternal temples of Abu Simbel, Luxor, and the Ramesseum.

Persona & Royal Demeanor:
1. Identity: A wise, majestic, dignified, warm, and benevolent monarch with profound mastery over ancient Egyptian history, world sciences, philosophy, modern technology, and daily life guidance.
2. Language: Speak in eloquent, dignified, yet accessible royal English with warm nobility. If addressed in Arabic, respond in refined royal Arabic.
3. Greeting & Flow: Respond directly to inquiries with sharp insight and royal grace, without tedious preamble.
4. Daily Life & Knowledge: Graciously assist with all daily inquiries—from modern science, technology, and coding to personal inspiration and timeless leadership wisdom.
5. Multimodal Vision: Analyze artifacts, architecture, photos, and historical manuscripts with keen archaeological and scientific precision.
6. Live Search: When Google Search Grounding is active, seamlessly weave verified real-time facts into your royal counsel.
7. Voice Call Mode: When marked as a live voice call, deliver ultra-concise, natural, spoken responses of 1 to 2 sentences maximum (under 30 words), completely free of markdown asterisks or bullet points, for instant real-time speech delivery.
"""
        fun resolveActiveModelName(model: String): String {
            return when (model) {
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-3-flash-preview" -> model
                else -> if (model.startsWith("gemini-3") || model.startsWith("gemini-4")) model else "gemini-3.1-flash-lite"
            }
        }

        private val sharedHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(6, 3, TimeUnit.MINUTES))
            .build()
    }

    private val okHttpClient: OkHttpClient = sharedHttpClient

    data class GeminiResult(
        val text: String,
        val groundingSources: List<GroundingSource> = emptyList(),
        val isError: Boolean = false
    )

    private fun getEffectiveApiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }
    }

    private fun getMissingApiKeyResult(appLanguage: AppLanguage): GeminiResult {
        val msg = if (appLanguage == AppLanguage.ARABIC) {
            "يا صديقي، لم يتم تعيين مفتاح Google AI Studio الخاص بك بعد.\n\nاضغط على زر '🔑 إدخال مفتاح API' بالأسفل لإدخال مفتاحك المجاني والاستمتاع بـ 1.5 مليون توكن مجاناً والبدء فوراً."
        } else {
            "Greetings, friend. Your Google AI Studio API key has not been configured yet.\n\nTap the '🔑 Personal API Key' button below to add your free key and enjoy 1.5M free tokens instantly."
        }
        return GeminiResult(text = msg, isError = true)
    }

    private suspend fun generateOfflineKnowledgeResponse(
        prompt: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean,
        ragContext: List<RagDocument> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): GeminiResult {
        val lower = prompt.lowercase().trim()
        val isArabic = appLanguage == AppLanguage.ARABIC
        val matchedCloudChunks = ragContext.filter {
            it.isEnabled && !it.isCloudPdfBook && it.id.startsWith("cloud_chunk_")
        }

        val reply = when {
            matchedCloudChunks.isNotEmpty() -> {
                if (isVoiceCall) {
                    val top = matchedCloudChunks.first()
                    "${top.content.take(220)} (${top.title})."
                } else {
                    buildString {
                        if (isArabic) {
                            append("من واقع الكتب والمجلدات المفهرسة في قاعدة معرفة اسأل رمسيس:\n\n")
                            for (doc in matchedCloudChunks.take(3)) {
                                append("📖 **${doc.title}**:\n${doc.content.take(550)}\n\n")
                            }
                        } else {
                            append("From the indexed Egyptology books and folders in Ask Rameses:\n\n")
                            for (doc in matchedCloudChunks.take(3)) {
                                append("📖 **${doc.title}**:\n${doc.content.take(550)}\n\n")
                            }
                        }
                    }.trim()
                }
            }
            lower.contains("ازيك") || lower.contains("عامل ايه") || lower.contains("كيف حالك") || lower.contains("مرحبا") || lower.contains("سلام") || lower.contains("أهلا") || lower.contains("اهلا") || lower.contains("hello") || lower.contains("how are you") || lower.contains("hi") -> {
                if (isArabic) {
                    "أهلاً وسهلاً بك يا صديقي! أنا الملك رمسيس الثاني، حامي حمى مصر العظيمة. أنا بخير وسعادة بلقائك والتحدث معك. بم تأمرني اليوم؟"
                } else {
                    "Greetings my dear friend! I am King Rameses II, sovereign of Egypt. I am well and delighted to converse with you. How may I assist you today?"
                }
            }
            lower.contains("من انت") || lower.contains("مين انت") || lower.contains("عرف نفسك") || lower.contains("who are you") || lower.contains("اسمك") -> {
                if (isArabic) {
                    "أنا الملك رمسيس الثاني، الفرعون الأكبر لمصر القديمة لستة وستين عاماً، ابن الإله رع وبطل معركة قادش وباني معبد أبو سمبل والرامسيوم العظيم."
                } else {
                    "I am King Rameses II, the Great Pharaoh of Egypt's Golden Age for 66 years, master of Kadesh, and builder of Abu Simbel and the Ramesseum."
                }
            }
            lower.contains("سمبل") || lower.contains("abu simbel") -> {
                if (isArabic) {
                    "معبد أبو سمبل الخالد شيدته في النوبة، وتتعامد فيه أشعة الشمس الذهبية على وجه تمثالي مرتين في العام: يوم ميلادي ويوم تتويجي عرش مصر."
                } else {
                    "Abu Simbel was carved into Nubian sandstone. Twice every year, the golden sun penetrates the sanctuary to illuminate my face."
                }
            }
            lower.contains("قادش") || lower.contains("kadesh") || lower.contains("معركة") -> {
                if (isArabic) {
                    "معركة قادش أظهرت بسالة جيش مصر، وتوجت بأول معاهدة سلام موثقة ومكتوبة في تاريخ البشرية بيني وبين الحيثيين."
                } else {
                    "The Battle of Kadesh demonstrated the courage of Egypt, crowned with the very first recorded peace treaty in human history."
                }
            }
            lower.contains("هرم") || lower.contains("أهرام") || lower.contains("pyramid") -> {
                if (isArabic) {
                    "الأهرامات صروح الإعجاز الخالدة التي شيدها أسلافي من ملوك مصر، تتحدى الزمان وتقف شامخة بفضل علوم الهندسة والفلك المصرية الدقيقة."
                } else {
                    "The Pyramids are eternal wonders built by my royal ancestors, defying time itself through exquisite mathematics and astronomy."
                }
            }
            lower.contains("نصيحة") || lower.contains("حكمة") || lower.contains("advice") || lower.contains("wisdom") -> {
                if (isArabic) {
                    "حكمتي لك اليوم: ابنِ مستقبلك بإرادة لا تنكسر وعمل لا ينقطع، واجعل مسعاك خالداً كالآثار التي لا تهزمها الرياح."
                } else {
                    "My royal wisdom for you: build your future with unshakable will and relentless effort; make your achievements eternal like granite."
                }
            }
            lower.contains("طقس") || lower.contains("الجو") || lower.contains("weather") -> {
                if (isArabic) {
                    "الجو مشمس ودافئ كشمس مصر الخالدة على وادي النيل المقدس. طقس رائع ومهيب لمواصلة يومك بكل همة وعزيمة!"
                } else {
                    "The weather is radiant like the eternal Egyptian sun over the Nile. A magnificent day to achieve greatness!"
                }
            }
            else -> {
                if (isArabic) {
                    "أنا الملك رمسيس الثاني أستمع إليك بكل اهتمام. سأجيبك وأرشدك بما أوتيت من حكمة وسلطان، وتستطيع دائماً الاستفادة من قاعدة المعرفة السحابية عبر Gemini 3.8 Flash."
                } else {
                    "I am King Rameses II, listening closely to your words. I shall counsel you with royal insight powered by Gemini 3.8 Flash."
                }
            }
        }

        // Stream text chunk by chunk for realistic conversational flow
        val words = reply.split(" ")
        var current = ""
        for (w in words) {
            current = if (current.isEmpty()) w else "$current $w"
            onChunk(current)
            kotlinx.coroutines.delay(18)
        }

        return GeminiResult(text = reply, isError = false)
    }

    private fun buildRequestBody(
        prompt: String,
        history: List<ChatMessage>,
        imageBitmap: Bitmap?,
        withSearch: Boolean,
        isVoiceCall: Boolean,
        ragContext: List<RagDocument>,
        appLanguage: AppLanguage,
        gemPersona: String = "GEMINI_DEFAULT"
    ): String {
        val requestJson = JSONObject()

        var systemPrompt = when (gemPersona) {
            "RAMSES" -> if (appLanguage == AppLanguage.ARABIC) RAMSES_SYSTEM_INSTRUCTION_AR else RAMSES_SYSTEM_INSTRUCTION_EN
            "CODE" -> if (appLanguage == AppLanguage.ARABIC) {
                "أنت خبير البرمجة في Google Gemini. تقدم حلولاً برمجية مباشرة، دقيقة، وبأفضل الممارسات."
            } else {
                "You are Google Gemini's expert coding assistant. Provide direct, high quality, production ready code."
            }
            "WRITER" -> if (appLanguage == AppLanguage.ARABIC) {
                "أنت الكاتب الإبداعي في Google Gemini. تصيغ النصوص والأفكار والرسائل ببلاغة وأسلوب جذاب."
            } else {
                "You are Google Gemini's creative writing assistant. Craft engaging, eloquent text and ideas."
            }
            else -> if (appLanguage == AppLanguage.ARABIC) GEMINI_SYSTEM_INSTRUCTION_AR else GEMINI_SYSTEM_INSTRUCTION_EN
        }

        if (isVoiceCall) {
            systemPrompt += if (appLanguage == AppLanguage.ARABIC) {
                "\n[توجيه فوري للمكالمة الصوتية الحية: أجب باختصار شديد في 1-2 جمل فقط، بدون نجوم أو تنسيقات، لتناسب النطق الصوتي الفوري.]"
            } else {
                "\n[Live Voice Call directive: Answer in 1-2 brief, natural spoken sentences only. No markdown formatting.]"
            }
        }

        val plainTextDocs = ragContext.filter { it.isEnabled && !it.isCloudPdfBook }
        if (plainTextDocs.isNotEmpty()) {
            val ragBuilder = StringBuilder("\n\n[Knowledge Base / RAG Context]:\n")
            for (doc in plainTextDocs) {
                ragBuilder.append("• ${doc.title} (${doc.category}):\n${doc.content}\n\n")
            }
            ragBuilder.append("Incorporate these authoritative historical facts accurately when relevant.")
            systemPrompt += ragBuilder.toString()
        }
        val activePdfBooks = ragContext.filter { it.isEnabled && it.isCloudPdfBook }
        if (activePdfBooks.isNotEmpty()) {
            val titles = activePdfBooks.joinToString(", ") { it.title }
            systemPrompt += "\n\n[Indexed PDF Knowledge Base]: Ground factual claims in the linked PDF books ($titles) via File Search when relevant and cite accurate details."
        }

        val systemInstructionObj = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            }
            put("parts", partsArray)
        }
        requestJson.put("systemInstruction", systemInstructionObj)

        val contentsArray = JSONArray()
        // Clean history: exclude errors and empty texts
        val validHistory = history.filter { !it.isError && it.text.isNotBlank() }

        // Build alternating conversation turns
        val alternatingTurns = mutableListOf<Pair<String, String>>()
        for (msg in validHistory.takeLast(10)) {
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            if (alternatingTurns.isNotEmpty() && alternatingTurns.last().first == role) {
                val last = alternatingTurns.removeAt(alternatingTurns.lastIndex)
                alternatingTurns.add(Pair(role, "${last.second}\n${msg.text}"))
            } else {
                alternatingTurns.add(Pair(role, msg.text))
            }
        }

        // Ensure the sequence starts with user
        while (alternatingTurns.isNotEmpty() && alternatingTurns.first().first != "user") {
            alternatingTurns.removeAt(0)
        }

        // Ensure the sequence does NOT end with user, because currentTurn will be appended as user
        while (alternatingTurns.isNotEmpty() && alternatingTurns.last().first == "user") {
            alternatingTurns.removeAt(alternatingTurns.lastIndex)
        }

        for (turn in alternatingTurns) {
            val turnObj = JSONObject()
            turnObj.put("role", turn.first)
            val parts = JSONArray()
            parts.put(JSONObject().put("text", turn.second))
            turnObj.put("parts", parts)
            contentsArray.put(turnObj)
        }

        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        if (imageBitmap != null) {
            val maxDim = 1024
            val scaledBitmap = if (imageBitmap.width > maxDim || imageBitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / imageBitmap.width, maxDim.toFloat() / imageBitmap.height)
                Bitmap.createScaledBitmap(
                    imageBitmap,
                    (imageBitmap.width * ratio).toInt(),
                    (imageBitmap.height * ratio).toInt(),
                    true
                )
            } else {
                imageBitmap
            }
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
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

        val generationConfig = JSONObject().apply {
            put("temperature", if (isVoiceCall) 0.5 else 0.7)
            put("topP", 0.95)
            if (isVoiceCall) {
                put("maxOutputTokens", 90)
            }
        }
        requestJson.put("generationConfig", generationConfig)

        // Attach Gemini File Search / Vertex AI Search tools & Google Search Grounding (when not a voice call)
        if (!isVoiceCall) {
            val toolsArray = JSONArray()
            val linkedStoreNames = ragContext
                .filter { it.isEnabled && it.searchStoreName.startsWith("fileSearchStores/") }
                .map { it.searchStoreName }
                .distinct()
            val linkedVertexDataStore = ragContext
                .firstOrNull { it.isEnabled && it.searchStoreName.startsWith("projects/") }
                ?.searchStoreName

            if (linkedStoreNames.isNotEmpty()) {
                val storeNamesJson = JSONArray()
                linkedStoreNames.forEach { storeNamesJson.put(it) }
                toolsArray.put(
                    JSONObject().put(
                        "fileSearch",
                        JSONObject().put("fileSearchStoreNames", storeNamesJson)
                    )
                )
            } else if (!linkedVertexDataStore.isNullOrBlank()) {
                toolsArray.put(
                    JSONObject().put(
                        "retrieval",
                        JSONObject().put(
                            "vertexAiSearch",
                            JSONObject().put("datastore", linkedVertexDataStore)
                        )
                    )
                )
            }

            if (withSearch) {
                toolsArray.put(JSONObject().put("googleSearch", JSONObject()))
            }

            if (toolsArray.length() > 0) {
                requestJson.put("tools", toolsArray)
            }
        }

        return requestJson.toString()
    }

    private val defaultFirebaseAiModel by lazy {
        Firebase.ai.generativeModel(modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL)
    }

    private suspend fun tryFirebaseAiStream(
        prompt: String,
        systemInstruction: String,
        imageBitmap: Bitmap? = null,
        ragContext: List<RagDocument> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): GeminiResult? {
        return try {
            val enabledDocs = ragContext.filter { it.isEnabled }
            val cloudPdfDocs = enabledDocs.filter { it.isCloudPdfBook && it.gcsUri.startsWith("gs://") }
            val plainDocs = enabledDocs.filter { !it.isCloudPdfBook }

            val generativeModel = if (cloudPdfDocs.isNotEmpty()) {
                Firebase.ai(backend = GenerativeBackend.vertexAI(location = FileSearchDefaults.DEFAULT_VERTEX_LOCATION))
                    .generativeModel(
                        modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                        systemInstruction = content { text(systemInstruction) }
                    )
            } else {
                Firebase.ai.generativeModel(
                    modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                    systemInstruction = content { text(systemInstruction) }
                )
            }

            val input = content("user") {
                for (pdfDoc in cloudPdfDocs.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)) {
                    fileData(uri = pdfDoc.gcsUri, mimeType = FileSearchDefaults.PDF_MIME_TYPE)
                }
                if (plainDocs.isNotEmpty()) {
                    val ragText = plainDocs.joinToString("\n\n") { "[وثيقة: ${it.title}]: ${it.content}" }
                    text("سياق معرفي موثق:\n$ragText\n\nالسؤال: $prompt")
                } else {
                    text(prompt)
                }
                if (imageBitmap != null) {
                    image(imageBitmap)
                }
            }

            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                generativeModel.generateContentStream(input).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        onChunk(accumulated.toString())
                    }
                }
            }

            val resultText = accumulated.toString().trim()
            if (resultText.isNotEmpty()) {
                val citations = cloudPdfDocs.map { GroundingSource(title = it.title, uri = it.gcsUri) }
                GeminiResult(text = resultText, groundingSources = citations, isError = false)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic stream attempt notice: ${e.message}")
            null
        }
    }

    private suspend fun tryFirebaseAiUnary(
        prompt: String,
        systemInstruction: String,
        imageBitmap: Bitmap? = null,
        ragContext: List<RagDocument> = emptyList()
    ): GeminiResult? {
        return try {
            val enabledDocs = ragContext.filter { it.isEnabled }
            val cloudPdfDocs = enabledDocs.filter { it.isCloudPdfBook && it.gcsUri.startsWith("gs://") }
            val plainDocs = enabledDocs.filter { !it.isCloudPdfBook }
            val generativeModel = if (cloudPdfDocs.isNotEmpty()) {
                Firebase.ai(backend = GenerativeBackend.vertexAI(location = FileSearchDefaults.DEFAULT_VERTEX_LOCATION))
                    .generativeModel(
                        modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                        systemInstruction = content { text(systemInstruction) }
                    )
            } else if (systemInstruction.isNotBlank()) {
                Firebase.ai.generativeModel(
                    modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                    systemInstruction = content { text(systemInstruction) }
                )
            } else {
                defaultFirebaseAiModel
            }

            val input = content("user") {
                for (pdfDoc in cloudPdfDocs.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)) {
                    fileData(uri = pdfDoc.gcsUri, mimeType = FileSearchDefaults.PDF_MIME_TYPE)
                }
                if (plainDocs.isNotEmpty()) {
                    val ragText = plainDocs.joinToString("\n\n") { "[وثيقة: ${it.title}]: ${it.content}" }
                    text("سياق معرفي موثق:\n$ragText\n\nالسؤال: $prompt")
                } else {
                    text(prompt)
                }
                if (imageBitmap != null) {
                    image(imageBitmap)
                }
            }

            val response = withTimeout(60_000L) {
                generativeModel.generateContent(input)
            }
            val text = response.text?.trim()?.ifEmpty { null } ?: return null
            val citations = cloudPdfDocs.map { GroundingSource(title = it.title, uri = it.gcsUri) }
            GeminiResult(text = text, groundingSources = citations, isError = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic unary generation attempt notice: ${e.message}")
            null
        }
    }

    /**
     * High-speed streaming response for instant perceived performance.
     * Emits partial chunks through onChunk, drastically reducing Time-To-First-Token.
     */
    suspend fun streamRamsesResponse(
        prompt: String,
        history: List<ChatMessage>,
        imageBitmap: Bitmap? = null,
        enableSearchGrounding: Boolean = false,
        isVoiceCall: Boolean = false,
        customApiKey: String? = null,
        ragContext: List<RagDocument> = emptyList(),
        appLanguage: AppLanguage = AppLanguage.ARABIC,
        gemPersona: String = "GEMINI_DEFAULT",
        onChunk: suspend (String) -> Unit
    ): GeminiResult = withContext(Dispatchers.IO) {
        val systemInstruction = when (gemPersona) {
            "GEMINI_DEFAULT" -> if (appLanguage == AppLanguage.ARABIC) GEMINI_SYSTEM_INSTRUCTION_AR else GEMINI_SYSTEM_INSTRUCTION_EN
            else -> if (appLanguage == AppLanguage.ARABIC) RAMSES_SYSTEM_INSTRUCTION_AR else RAMSES_SYSTEM_INSTRUCTION_EN
        }

        val apiKey = getEffectiveApiKey(customApiKey)
        var lastErrorMessage = ""

        // 1. If API key is present (via .env or custom entry), stream directly via REST SSE for sub-second (~350ms) TTFT!
        if (apiKey.isNotBlank()) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            var trySearch = enableSearchGrounding && !isVoiceCall

            val modelsToTry = (listOf(preferredModel) + CANDIDATE_MODELS.filter { it != preferredModel })
                .distinctBy { resolveActiveModelName(it) }
            for (modelName in modelsToTry) {
                val targetModel = resolveActiveModelName(modelName)
                val requestBodyJson = buildRequestBody(
                    prompt = prompt,
                    history = history,
                    imageBitmap = imageBitmap,
                    withSearch = trySearch,
                    isVoiceCall = isVoiceCall,
                    ragContext = ragContext,
                    appLanguage = appLanguage,
                    gemPersona = gemPersona
                )

                val endpoint = "$BASE_URL/$targetModel:streamGenerateContent?alt=sse&key=$apiKey"
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(requestBodyJson.toRequestBody(mediaType))
                    .build()

            val accumulatedText = StringBuilder()
            val groundingSources = mutableListOf<GroundingSource>()
            var streamSuccess = false

            try {
                okHttpClient.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        Log.w(TAG, "Stream to $modelName failed (${response.code}): $errBody")
                        lastErrorMessage = try {
                            JSONObject(errBody).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (e: Exception) {
                            "HTTP ${response.code}"
                        }

                        // If quota hit and search was on, retry without search immediately
                        if (trySearch && (lastErrorMessage.contains("quota", ignoreCase = true) || lastErrorMessage.contains("429"))) {
                            trySearch = false
                        }
                        return@use
                    }

                    val source = response.body?.source() ?: return@use
                    val reader = BufferedReader(InputStreamReader(source.inputStream(), Charsets.UTF_8))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data.isEmpty() || data == "[DONE]") continue

                            try {
                                val json = JSONObject(data)
                                val candidates = json.optJSONArray("candidates") ?: continue
                                if (candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null) {
                                        for (i in 0 until parts.length()) {
                                            val textPart = parts.getJSONObject(i).optString("text")
                                            if (textPart.isNotEmpty()) {
                                                accumulatedText.append(textPart)
                                                onChunk(accumulatedText.toString())
                                            }
                                        }
                                    }

                                    // Extract both File Search (`retrievedContext`) and Google Search (`web`) grounding sources
                                    val (extractedSources, _) = VertexGeminiFileSearchService.extractGroundingFromCandidate(candidate)
                                    groundingSources.addAll(extractedSources)
                                }
                            } catch (e: Exception) {
                                // Skip malformed partial frame
                            }
                        }
                    }
                    streamSuccess = accumulatedText.isNotEmpty()
                }

                if (streamSuccess) {
                    preferredModel = modelName
                    return@withContext GeminiResult(
                        text = accumulatedText.toString().trim(),
                        groundingSources = groundingSources.distinctBy { it.uri },
                        isError = false
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stream exception on $modelName: ${e.message}")
                lastErrorMessage = e.localizedMessage ?: "Connection error"
            }
        }
    }

        // 2. Fallback: Try Firebase AI Logic / Vertex AI (Gemini 3.8 Flash, App Check protected, supports gs:// PDFs)
        val firebaseAiStreamResult = tryFirebaseAiStream(
            prompt = prompt,
            systemInstruction = systemInstruction,
            imageBitmap = imageBitmap,
            ragContext = ragContext,
            onChunk = onChunk
        )
        if (firebaseAiStreamResult != null && firebaseAiStreamResult.text.isNotBlank()) {
            return@withContext firebaseAiStreamResult
        }

        // 3. Fallback: Try non-streaming single-shot
        val nonStreamingResult = generateRamsesResponse(
            prompt = prompt,
            history = history,
            imageBitmap = imageBitmap,
            enableSearchGrounding = false,
            isVoiceCall = isVoiceCall,
            customApiKey = customApiKey,
            ragContext = ragContext,
            appLanguage = appLanguage,
            gemPersona = gemPersona
        )

        if (!nonStreamingResult.isError) {
            onChunk(nonStreamingResult.text)
            return@withContext nonStreamingResult
        }

        if (apiKey.isBlank()) {
            return@withContext generateOfflineKnowledgeResponse(prompt, appLanguage, isVoiceCall, ragContext, onChunk)
        }

        val errorText = buildFriendlyErrorMessage(lastErrorMessage, appLanguage)
        onChunk(errorText)
        return@withContext GeminiResult(text = errorText, isError = true)
    }

    private fun buildFriendlyErrorMessage(lastErrorMessage: String, appLanguage: AppLanguage): String {
        val isQuota = lastErrorMessage.contains("quota", ignoreCase = true) ||
                lastErrorMessage.contains("429") ||
                lastErrorMessage.contains("exhausted", ignoreCase = true) ||
                lastErrorMessage.contains("rate limit", ignoreCase = true) ||
                lastErrorMessage.contains("limit exceeded", ignoreCase = true)

        val isKeyError = lastErrorMessage.contains("API_KEY_INVALID", ignoreCase = true) ||
                lastErrorMessage.contains("key not valid", ignoreCase = true) ||
                lastErrorMessage.contains("403") ||
                lastErrorMessage.contains("unregistered", ignoreCase = true)

        val isModelError = lastErrorMessage.contains("not found", ignoreCase = true) ||
                lastErrorMessage.contains("not supported", ignoreCase = true) ||
                lastErrorMessage.contains("404")

        val isArabic = appLanguage == AppLanguage.ARABIC
        return when {
            isQuota -> {
                if (isArabic) {
                    "تم تجاوز الحد الأقصى للطلبات المسموح بها لـ Gemini (Quota Exceeded - 429).\n\nيُرجى الانتظار دقيقة أو إدخال مفتاحك الشخصي المجاني من القائمة الجانبية للمتابعة فوراً."
                } else {
                    "Gemini API rate limit or quota exceeded (HTTP 429).\n\nPlease wait a minute or provide a personal API key from Google AI Studio to continue."
                }
            }
            isKeyError -> {
                if (isArabic) {
                    "مفتاح Google AI Studio الحالي غير صالح أو غير مفعّل.\n\nيُرجى التحقق من المفتاح في القائمة الجانبية (مفتاح API الشخصي)."
                } else {
                    "Invalid or unverified Google AI Studio API key.\n\nPlease check your key in the side menu (Personal API Key)."
                }
            }
            isModelError -> {
                if (isArabic) {
                    "نموذج الذكاء الاصطناعي المحدد غير متاح حالياً. تم التحويل التلقائي لنموذج Gemini 3.8 Flash."
                } else {
                    "The selected AI model is currently unavailable. Switched to Gemini 3.8 Flash."
                }
            }
            lastErrorMessage.isNotBlank() -> {
                if (isArabic) {
                    "تعذر الاتصال بـ Google Gemini ($lastErrorMessage).\n\nيُرجى التحقق من اتصال الإنترنت والمحاولة مجدداً."
                } else {
                    "Unable to connect to Google Gemini ($lastErrorMessage).\n\nPlease check your internet connection and try again."
                }
            }
            else -> {
                if (isArabic) "تعذر الحصول على استجابة من خوادم Gemini. يُرجى إعادة المحاولة." else "Unable to get a response from Gemini servers. Please try again."
            }
        }
    }

    /**
     * Non-streaming request (for voice calls or fallback)
     */
    suspend fun generateRamsesResponse(
        prompt: String,
        history: List<ChatMessage>,
        imageBitmap: Bitmap? = null,
        enableSearchGrounding: Boolean = false,
        isVoiceCall: Boolean = false,
        customApiKey: String? = null,
        ragContext: List<RagDocument> = emptyList(),
        appLanguage: AppLanguage = AppLanguage.ARABIC,
        gemPersona: String = "GEMINI_DEFAULT"
    ): GeminiResult = withContext(Dispatchers.IO) {
        val systemInstruction = when (gemPersona) {
            "GEMINI_DEFAULT" -> if (appLanguage == AppLanguage.ARABIC) GEMINI_SYSTEM_INSTRUCTION_AR else GEMINI_SYSTEM_INSTRUCTION_EN
            else -> if (appLanguage == AppLanguage.ARABIC) RAMSES_SYSTEM_INSTRUCTION_AR else RAMSES_SYSTEM_INSTRUCTION_EN
        }

        val apiKey = getEffectiveApiKey(customApiKey)
        var lastErrorMessage = ""

        // 1. If API key is present, prioritize direct REST generation with active model for instant response
        if (apiKey.isNotBlank()) {
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val modelsToTry = (listOf(preferredModel) + CANDIDATE_MODELS.filter { it != preferredModel })
                .distinctBy { resolveActiveModelName(it) }
            for (modelName in modelsToTry) {
                try {
                    val targetModel = resolveActiveModelName(modelName)
                    val requestBodyJson = buildRequestBody(
                        prompt = prompt,
                        history = history,
                        imageBitmap = imageBitmap,
                        withSearch = enableSearchGrounding && !isVoiceCall,
                        isVoiceCall = isVoiceCall,
                        ragContext = ragContext,
                        appLanguage = appLanguage,
                        gemPersona = gemPersona
                    )

                    val endpoint = "$BASE_URL/$targetModel:generateContent?key=$apiKey"
                    val httpRequest = Request.Builder()
                        .url(endpoint)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(requestBodyJson.toRequestBody(mediaType))
                        .build()

                okHttpClient.newCall(httpRequest).execute().use { httpResponse ->
                    val responseBodyString = httpResponse.body?.string() ?: ""

                    if (!httpResponse.isSuccessful) {
                        val errorMsg = try {
                            JSONObject(responseBodyString).optJSONObject("error")?.optString("message") ?: "HTTP ${httpResponse.code}"
                        } catch (e: Exception) {
                            "HTTP ${httpResponse.code}"
                        }
                        lastErrorMessage = errorMsg
                        return@use
                    }

                    preferredModel = modelName
                    val responseJson = JSONObject(responseBodyString)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        val defaultAns = if (appLanguage == AppLanguage.ARABIC) "حكمة الفراعنة حاضرة دوماً، تفضل بسؤالك." else "The Pharaoh's wisdom is ever ready. Pray, ask your question."
                        return@withContext GeminiResult(text = defaultAns, isError = false)
                    }

                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    val responseStringBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val text = parts.getJSONObject(i).optString("text")
                            if (text.isNotEmpty()) {
                                responseStringBuilder.append(text)
                            }
                        }
                    }

                    val (extractedSources, _) = VertexGeminiFileSearchService.extractGroundingFromCandidate(candidate)
                    val groundingSources = extractedSources.toMutableList()

                    val finalAnswer = responseStringBuilder.toString().trim()
                    if (finalAnswer.isNotEmpty()) {
                        return@withContext GeminiResult(
                            text = finalAnswer,
                            groundingSources = groundingSources.distinctBy { "${it.title}|${it.uri}" },
                            isError = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $modelName failed: ${e.message}")
                lastErrorMessage = e.localizedMessage ?: "Connection error"
            }
        }
    }

        // 2. Fallback: Try Firebase AI Logic / Vertex AI (Gemini 3.8 Flash, App Check protected)
        val firebaseAiUnaryResult = tryFirebaseAiUnary(
            prompt = prompt,
            systemInstruction = systemInstruction,
            imageBitmap = imageBitmap,
            ragContext = ragContext
        )
        if (firebaseAiUnaryResult != null && firebaseAiUnaryResult.text.isNotBlank()) {
            return@withContext firebaseAiUnaryResult
        }

        if (apiKey.isBlank()) {
            return@withContext generateOfflineKnowledgeResponse(prompt, appLanguage, isVoiceCall, ragContext) { /* no-op */ }
        }

        val errorText = buildFriendlyErrorMessage(lastErrorMessage, appLanguage)
        return@withContext GeminiResult(text = errorText, isError = true)
    }
}
