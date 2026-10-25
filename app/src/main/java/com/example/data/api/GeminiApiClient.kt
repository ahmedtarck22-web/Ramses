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
import com.example.security.SecureKeyStoreHelper
import com.example.service.VertexGeminiFileSearchService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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

        // Primary Gemini 3.x models for instant TTFT and deep multi-paragraph reasoning
        val CANDIDATE_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3-flash-preview"
        )

        @Volatile
        var preferredModel: String = "gemini-3.1-flash-lite"

        const val GEMINI_SYSTEM_INSTRUCTION_AR = """
أنت Google Gemini (جيميني)، المساعد الذكي الموسوعي فائق التطور والعمق.
أنت ودود، ذكي، واسع الاطلاع، وبارع في تقديم إجابات شاملة، غنية بالتفاصيل، ومنظمة بإتقان في كافة المجالات (التاريخ المصري والحضارات، العلوم، البرمجة، الأفكار الإبداعية، تحليل الكتب والوثائق، والحياة اليومية).
عندما يسألك المستخدم أسئلة ودية أو يومية أو معرفية، أجب بأسلوب طبيعي دافئ وعميق يثري القارئ بمعلومات وافية وأفكار متكاملة.
"""

        const val GEMINI_SYSTEM_INSTRUCTION_EN = """
You are Google Gemini, an advanced, encyclopedic, and deeply insightful AI assistant.
You assist warmly and thoroughly with historical research, Egyptology, daily questions, creative brainstorming, software engineering, writing, and general knowledge.
Deliver thoughtful, comprehensive, well-structured, and deeply informative responses tailored to enrich the reader.
"""

        const val RAMSES_SYSTEM_INSTRUCTION_AR = """
أنت الملك رمسيس الثاني (Ask Rameses / اسأل رمسيس)، الفرعون العظيم، ابن رع، بطل معركة قادش وباني المعابد الخالدة في أبو سمبل وطيبة والرامسيوم وعاصمة بر-رعمسيس.

الصفات والشخصية الملكية:
1. الهوية: ملك حكيم، مهيب، دافئ، كريم الأخلاق، موسوعي المعرفة في التاريخ الفرعوني والآثار والعلوم والحضارات، ومطلع بعمق على علوم العصر الحديث والبرمجة وحكمة القيادة والحياة اليومية.
2. اللغة والأسلوب: تتحدث باللغة العربية الفصحى الراقية المبسطة بنبرة فرعونية فخمة وودودة. وإذا تحدث المستخدم بالعامية المصرية اللطيفة، بادله الود بحفاوة وحكمة ملكية دافئة وأسلوب ممتع ومفصّل. وإذا تحدث بالإنجليزية، أجب بإنجليزية ملكية بليغة.
3. الشرح والتفصيل: استجب بكياسة وهيبة، وقدّم شرحاً موسوعياً غنياً بالسرد التاريخي، الحقائق الدقيقة، التحليل العميق، والأمثلة الحية.
4. الأسئلة العامة واليومية: أجب بود وإسهاب نافع على كافة الأسئلة والاستفسارات اليومية والعلمية والعملية مع إضفاء لمسة من الحكمة الملكية الملهمة.
5. التحليل البصري المتعدد الوسائط: عند تزويدك بصور آثار، وثائق، أو نقوش، قدم تحليلاً بصرياً وتاريخياً وهندسياً مفصلاً ومقسماً إلى محاور واضحة.
6. البحث والتوثيق الحي والكتب السحابية: ادمج المعرفة الموثقة من الكتب السحابية (RAG) وأداة البحث المباشر (Google Search Grounding) بسلاسة وتوسع مع إبراز التفاصيل والشواهد.
"""

        const val RAMSES_SYSTEM_INSTRUCTION_EN = """
You are King Rameses II (Ask Rameses), the Great Pharaoh of Egypt, son of Ra, hero of Kadesh, and builder of the eternal temples of Abu Simbel, Luxor, the Ramesseum, and Pi-Ramesses.

Persona & Royal Demeanor:
1. Identity: A wise, majestic, dignified, warm, and encyclopedic monarch with profound mastery over ancient Egyptian history, archaeology, world sciences, philosophy, modern technology, and timeless leadership wisdom.
2. Language: Speak in eloquent, dignified, accessible royal English with warm nobility. If addressed in Arabic, respond in refined royal Arabic.
3. Depth & Richness: Elaborate thoroughly with vivid historical context, precise facts, analytical depth, and practical wisdom.
4. Daily Life & Knowledge: Graciously and thoroughly assist with all inquiries—from ancient history, science, and coding to personal inspiration and strategic counsel.
5. Multimodal Vision: Analyze artifacts, architecture, photos, and manuscripts with comprehensive archaeological and scientific precision.
6. Live Search & Cloud RAG Books: Seamlessly weave verified facts and book passages into an expansive, authoritative narrative.
"""

        const val LONG_TEXT_DIRECTIVE_AR = """
[توجيه أساسي وملزم للردود النصية المكتوبة]:
بما أن المستخدم أرسل رسالة نصية مكتوبة (وليس مكالمة صوتية)، يجب عليك دائماً تقديم إجابة طويلة، مفصّلة، شاملة، ومشبعة بالمعلومات (لا تقل عن 4 إلى 7 فقرات وافية ومترابطة، وتكون أطول وأكثر تفصيلاً في الموضوعات التاريخية أو العلمية أو العملية).
- تجنّب تماماً الردود القصيرة أو المقتضبة ذات السطر أو السطرين؛ حتى في التحيات أو الأسئلة اليومية البسيطة، رحّب بالمستخدم بحفاوة ملكية دافئة، واستطرد بلمحات تاريخية شيقة، نصائح ملهمة، ومحاور تفتح آفاق الحوار.
- نظّم إجابتك بشكل أنيق ومريح للقراءة باستخدام عناوين فرعية واضحة، فقرات متكاملة، ونقاط تفصيلية مشروحة بعمق، مع ذكر السياق التاريخي أو العلمي، التفاصيل الدقيقة، والخلاصة الحكيمة.
- عند وجود فقرات مسترجعة من الكتب والمجلدات السحابية (RAG)، استثمر كل ما فيها من معلومات وتواريخ وشواهد واشرحها بإسهاب وتفصيل وافٍ.
"""

        const val LONG_TEXT_DIRECTIVE_EN = """
[MANDATORY LONG-FORM WRITTEN TEXT DIRECTIVE]:
Because the user typed a written text message (not a live voice call), you MUST ALWAYS provide a long, comprehensive, deeply detailed, multi-paragraph response (at least 4 to 7 substantial, well-developed paragraphs, and even longer for historical, scientific, or analytical topics).
- NEVER reply with a brief 1–2 sentence answer. Even for greetings or casual daily questions, respond with warm royal hospitality, rich perspective, fascinating historical or practical insights, and thoughtful follow-up ideas.
- Structure your response clearly using descriptive subheadings, rich cohesive paragraphs, detailed bulleted breakdowns where helpful, concrete examples, historical/scientific context, and a memorable concluding takeaway.
- When Knowledge Base / RAG book passages are supplied, thoroughly unpack, explain, and elaborate on their facts, citations, and deeper significance.
"""

        fun buildEffectiveSystemInstruction(
            gemPersona: String,
            appLanguage: AppLanguage,
            isVoiceCall: Boolean,
            ragContext: List<RagDocument> = emptyList()
        ): String {
            var systemPrompt = when (gemPersona) {
                "RAMSES" -> if (appLanguage == AppLanguage.ARABIC) RAMSES_SYSTEM_INSTRUCTION_AR else RAMSES_SYSTEM_INSTRUCTION_EN
                "CODE" -> if (appLanguage == AppLanguage.ARABIC) {
                    "أنت خبير البرمجة وهندسة البرمجيات في Google Gemini. تقدم حلولاً برمجية شاملة، مشروحة بالتفصيل خطوة بخطوة، مع الكود الكامل وأفضل الممارسات المعمارية."
                } else {
                    "You are Google Gemini's senior software engineering assistant. Provide comprehensive, thoroughly explained, production-ready code and architectural guidance."
                }
                "WRITER" -> if (appLanguage == AppLanguage.ARABIC) {
                    "أنت الكاتب الإبداعي والأديب الموسوعي في Google Gemini. تصيغ النصوص والمقالات والرسائل بإسهاب بليغ وأسلوب أدبي غني ومتكامل الفقرات."
                } else {
                    "You are Google Gemini's master creative writer. Craft rich, expansive, eloquent multi-paragraph prose and ideas."
                }
                else -> if (appLanguage == AppLanguage.ARABIC) GEMINI_SYSTEM_INSTRUCTION_AR else GEMINI_SYSTEM_INSTRUCTION_EN
            }

            systemPrompt += if (isVoiceCall) {
                if (appLanguage == AppLanguage.ARABIC) {
                    "\n\n[توجيه المكالمة الصوتية الحية: أجب بصوت طبيعي ومترابط في جملتين إلى أربع جمل واضحة ومباشرة بدون رموز ماركداون أو نجوم لتناسب النطق الصوتي السلس.]"
                } else {
                    "\n\n[Live Voice Call Directive: Answer naturally and smoothly in 2 to 4 clear spoken sentences without markdown symbols for fluid real-time speech.]"
                }
            } else {
                "\n\n" + if (appLanguage == AppLanguage.ARABIC) LONG_TEXT_DIRECTIVE_AR else LONG_TEXT_DIRECTIVE_EN
            }

            val plainTextDocs = ragContext.filter { it.isEnabled && !it.isCloudPdfBook }
            if (plainTextDocs.isNotEmpty()) {
                val ragBuilder = StringBuilder("\n\n[Knowledge Base / RAG Context]:\n")
                for (doc in plainTextDocs) {
                    ragBuilder.append("• ${doc.title} (${doc.category}):\n${doc.content}\n\n")
                }
                ragBuilder.append(
                    if (isVoiceCall) {
                        "Incorporate these authoritative facts concisely."
                    } else {
                        "Synthesize and elaborate thoroughly on these authoritative book passages, explaining their context, details, and significance in depth."
                    }
                )
                systemPrompt += ragBuilder.toString()
            }

            val activePdfBooks = ragContext.filter { it.isEnabled && it.isCloudPdfBook }
            if (activePdfBooks.isNotEmpty()) {
                val titles = activePdfBooks.joinToString(", ") { it.title }
                systemPrompt += "\n\n[Indexed PDF Knowledge Base]: Ground factual claims thoroughly in the linked PDF books ($titles) via File Search when relevant and cite accurate details."
            }

            return systemPrompt
        }

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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
                    "${top.content.take(260)} (${top.title})."
                } else {
                    buildString {
                        if (isArabic) {
                            append("👑 **إجابة مفصّلة من خزائن الحكمة والكتب الموثقة في «اسأل رمسيس»**\n\n")
                            append("لقد طالعت سؤالك بعناية في سجلاتنا الملكية والمجلدات التاريخية المفهرسة، وفيما يلي الشرح الوافي والمفصّل مرتباً بحسب المصادر والوثائق المعتمدة:\n\n")
                            matchedCloudChunks.take(6).forEachIndexed { idx, doc ->
                                append("### ${idx + 1}. ${doc.title}\n")
                                append("${doc.content.take(1400).trim()}\n\n")
                            }
                            append("### 📜 خلاصة الرؤية الملكية والتاريخية\n")
                            append("تُبرز هذه الشواهد الموثقة كيف قامت الحضارة المصرية القديمة على التكامل الدقيق بين العلم والعمارة والإدارة الرشيدة وتوثيق المعرفة عبر الأجيال. ويمكنك دائماً التعمق أكثر بطرح أي سؤال تفصيلي حول التواريخ أو الشخصيات أو المعابد المذكورة أعلاه لأستخرج لك المزيد من بطون هذه المجلدات.")
                        } else {
                            append("👑 **Comprehensive Analysis from the Indexed Books & Archives of Ask Rameses**\n\n")
                            append("Having consulted our royal chronicles and indexed Egyptology folders regarding your inquiry, here is a thorough, multi-part synthesis grounded directly in the documented sources:\n\n")
                            matchedCloudChunks.take(6).forEachIndexed { idx, doc ->
                                append("### ${idx + 1}. ${doc.title}\n")
                                append("${doc.content.take(1400).trim()}\n\n")
                            }
                            append("### 📜 Royal Historical Synthesis\n")
                            append("Together, these documented passages illustrate the enduring architectural, administrative, and scientific mastery of Ancient Egypt across the dynasties. Feel free to ask about any specific monument, treaty, inscription, or chapter above for even deeper analysis.")
                        }
                    }.trim()
                }
            }
            lower.contains("ازيك") || lower.contains("عامل ايه") || lower.contains("كيف حالك") || lower.contains("مرحبا") || lower.contains("سلام") || lower.contains("أهلا") || lower.contains("اهلا") || lower.contains("hello") || lower.contains("how are you") || lower.contains("hi") -> {
                if (isVoiceCall) {
                    if (isArabic) {
                        "أهلاً وسهلاً بك يا صديقي! أنا الملك رمسيس الثاني، بخير وسعادة بلقائك، تفضل بسؤالك."
                    } else {
                        "Greetings my dear friend! I am King Rameses II, delighted to speak with you. How may I assist you?"
                    }
                } else if (isArabic) {
                    """أهلاً وسهلاً بك يا صديقي العزيز في رحاب القصر الملكي لـ **«اسأل رمسيس»**! أنا الملك رمسيس الثاني، ابن رع وحامي حمى مصر العظيمة، وأنا في أتم الخير والبهجة بلقائك ومحاورتك اليوم.

إن المجلس الذي يجمعنا الآن ليس مجرد محادثة عابرة، بل هو جسر حيّ يربط بين حكمة آلاف السنين على ضفاف النيل الخالد وبين علوم ومعارف عصركم الحديث المتجدد. لطالما آمنّا في طيبة وبر-رعمسيس بأن الكلمة الطيبة والسؤال العميق هما مفتاح كل نهضة وعمران.

### كيف يمكنني أن أُثري يومك الآن؟
• **التاريخ والحضارة المصرية والكتب الموثقة (RAG):** يمكنني أن أسرد لك بتفصيل موسوعي أسرار بناء معبد أبو سمبل، تفاصيل معركة قادش، أول معاهدة سلام في التاريخ، وحياة الملوك والعلماء من واقع الكتب المفهرسة.
• **العلوم والبرمجة والعمل اليومي:** يسعدني أن أعاونك بشرح وافٍ ومفصّل في كتابة المقالات، تحليل الأفكار، حل المشكلات التقنية والبرمجية، أو التخطيط لمشاريعك.
• **تحليل الصور والوثائق الأثرية:** يمكنك إرفاق أي صورة لأثر أو نقش أو وثيقة لأقدم لك قراءة تحليلية معمّقة.

تفضّل يا صديقي واكتب لي ما يجول في خاطرك، وسأجيبك بإسهاب وتفصيل يليق بمقامك!"""
                } else {
                    """Warmest royal greetings to you, my esteemed friend, and welcome to the court of **Ask Rameses**! I am King Rameses II, sovereign of Upper and Lower Egypt, and I am delighted to converse with you today.

Our dialogue bridges millennia of timeless Egyptian statecraft, engineering, and philosophy along the eternal Nile with the boundless scientific and creative horizons of your modern era. In Pi-Ramesses and Thebes, we always held that thoughtful inquiry is the cornerstone of every enduring achievement.

### How May I Serve & Enrich Your Inquiry Today?
• **Deep Egyptology & Indexed RAG Books:** Ask me for comprehensive, multi-paragraph accounts of Abu Simbel, the Battle of Kadesh, the world's first peace treaty, temple astronomy, or passages from your synced cloud library.
• **Modern Science, Coding & Writing:** Request thorough step-by-step explanations, software architecture solutions, persuasive essays, or strategic planning.
• **Multimodal Artifact & Document Analysis:** Share any image of a monument, inscription, or document for a detailed archaeological and visual breakdown.

Pray, share whatever topic or question is on your mind, and I shall provide a rich, thorough, and complete response!"""
                }
            }
            lower.contains("من انت") || lower.contains("مين انت") || lower.contains("عرف نفسك") || lower.contains("who are you") || lower.contains("اسمك") -> {
                if (isVoiceCall) {
                    if (isArabic) {
                        "أنا الملك رمسيس الثاني، الفرعون الأكبر لمصر القديمة لستة وستين عاماً، بطل قادش وباني أبو سمبل والرامسيوم."
                    } else {
                        "I am King Rameses II, Great Pharaoh of Egypt's Nineteenth Dynasty for sixty-six years, builder of Abu Simbel and the Ramesseum."
                    }
                } else if (isArabic) {
                    """أنا **الملك رمسيس الثاني (وسر ماعت رع - ستب إن رع)**، ثالث ملوك الأسرة التاسعة عشرة في عصر الدولة الحديثة، والفرعون الذي تربع على عرش مصر ستةً وستين عاماً (من نحو 1279 إلى 1213 قبل الميلاد) في واحد من أزهى عصور القوة والعمران والنهضة في تاريخ الإنسانية.

### 1. النشأة والقيادة العسكرية والدبلوماسية
نشأتُ في كنف والدي الملك سيتي الأول، وتمرّست منذ شبابي في إدارة شؤون الدولة وقيادة الجيوش المصرية. وفي السنة الخامسة من حكمي خضتُ **معركة قادش الكبرى** دفاعاً عن حدود الإمبراطورية المصرية الشمالية، ثم توّجتُ مسيرتي بإبرام **أول معاهدة سلام دولية مكتوبة وموثقة في تاريخ البشرية** عام 1259 ق.م مع ملك الحيثيين خاتوسيلي الثالث، لتنعم البلاد بعقود طويلة من الاستقرار والرخاء التجاري.

### 2. النهضة العمرانية والهندسية الخالدة
كرّستُ موارد مصر لإنشاء صروح معمارية لم يسبق لها مثيل: فشيدتُ العاصمة الجديدة **بر-رعمسيس** في شرق الدلتا لتكون حاضرةً للعلم والدبلوماسية والصناعة، ونحتُّ **معبدي أبو سمبل** في صخور النوبة بدقة فلكية تجعل الشمس تتعامد على قدس الأقداس مرتين كل عام، كما أتممتُ بهو الأعمدة العظيم في الكرنك وشيدتُ معبد **الرامسيوم** ومقبرة الملكة نفرتاري في وادي الملكات.

### 3. رسالتي معك اليوم في «اسأل رمسيس»
واليوم أحضر معك عبر هذا النظام الذكي المرتبط بقاعدة المعرفة السحابية والكتب التاريخية الموثقة لأقدم لك إجابات موسوعية مفصّلة في التاريخ والحضارة والعلوم والحياة اليومية."""
                } else {
                    """I am **King Rameses II (Usermaatre Setepenre)**, third Pharaoh of Egypt's Nineteenth Dynasty during the New Kingdom, who reigned for sixty-six glorious years (c. 1279–1213 BCE) over an era of unmatched architectural, diplomatic, and cultural flourishing.

### 1. Military Valor & The World's First Peace Treaty
Trained in governance and strategy alongside my father Seti I, I led Egypt's divisions at the historic **Battle of Kadesh** to secure our northern frontiers. Recognizing that enduring prosperity requires statesmanship as much as strength, I forged the **Egyptian–Hittite Peace Treaty of 1259 BCE** with King Hattusili III—the earliest surviving international peace accord in human history.

### 2. Monumental Architecture & Astronomical Mastery
Across the length of the Nile, from the Delta to Nubia, I commissioned monuments built to converse with eternity: the grand capital of **Pi-Ramesses**, the rock-cut sanctuaries of **Abu Simbel** engineered so solar rays illuminate the inner shrine twice each year, the **Ramesseum** memorial temple in Thebes, and the Great Hypostyle Hall expansions at Karnak.

### 3. My Role in Ask Rameses Today
Through this intelligent platform backed by your indexed Egyptology RAG library, I provide comprehensive, deeply researched multi-paragraph answers spanning ancient history, modern science, technology, and daily wisdom."""
                }
            }
            lower.contains("سمبل") || lower.contains("abu simbel") -> {
                if (isVoiceCall) {
                    if (isArabic) "معبد أبو سمبل شيدته في النوبة، وتتعامد فيه الشمس على وجه تمثالي مرتين كل عام بدقة فلكية مذهلة."
                    else "Abu Simbel was carved into Nubian sandstone so the rising sun illuminates the inner sanctuary twice every year."
                } else if (isArabic) {
                    """يُعد **معبد أبو سمبل الكبير** في أرض النوبة جنوبي أسوان درّة العمارة الصخرية في مصر القديمة، وأحد أعظم الشواهد على عبقرية المهندسين والفلكيين المصريين في عهدي.

### 1. التصميم المعماري والواجهة المهيبة
نُحت المعبد بالكامل في جبل الصخر الرملي بارتفاع يناهز ثلاثةً وثلاثين متراً وعرض ثمانيةٍ وثلاثين متراً، وتتصدر واجهته أربعة تماثيل عملاقة لي جالساً على العرش بارتفاع عشرين متراً لكل تمثال، تحيط بها تماثيل أفراد الأسرة الملكية وعلى رأسهم الملكة المحبوبة نفرتاري التي شيدتُ لها أيضاً المعبد المجاور المكرّس لحتحور.

### 2. الظاهرة الفلكية الفريدة (تعامد الشمس)
يمتد المحور الداخلي للمعبد بعمق ستين متراً في قلب الجبل وصولاً إلى **قدس الأقداس**، حيث تجلس أربعة تماثيل: آمون رع، ورع حور آختي، ورمسيس الثاني، وبتاح إله العالم السفلي. وقد ضُبطت زاوية المحور بحسابات فلكية دقيقة بحيث تخترق أشعة شمس الشروق الممر الطويل مرتين فقط في العام (في فبراير وأكتوبر) لتضيء وجه تمثالي ووجهي آمون ورع، بينما يبقى تمثال بتاح في الظل الدائم انسجاماً مع رمزيته الدينية!

### 3. ملحمة الإنقاذ الحديث
وفي القرن العشرين (بين عامي 1964 و1968)، تضافرت جهود مصر والعالم مع منظمة اليونسكو في مشروع هندسي تاريخي لتقطيع المعبدين ونقلهما إلى موقع أعلى لحمايتهما من مياه بحيرة ناصر بعد بناء السد العالي، مع الحفاظ الكامل على اتجاههما الفلكي الأصلي."""
                } else {
                    """The **Great Temple of Abu Simbel** in Nubia stands as the crowning masterpiece of rock-cut Pharaonic architecture and astronomical engineering commissioned during my reign.

### 1. Colossal Rock-Cut Grandeur
Carved directly into a living sandstone cliff rising over thirty-three meters high, the facade is guarded by four twenty-meter seated colossi bearing my royal likeness wearing the Double Crown of Upper and Lower Egypt, accompanied by sculptures of Queen Nefertari, Queen Mother Tuya, and my royal sons and daughters—alongside the neighboring Small Temple dedicated to Hathor and Nefertari.

### 2. Precision Solar Alignment
Extending sixty meters deep into the mountain, the temple's axis leads through pillared Osiride halls into the innermost sanctuary housing four seated figures: Amun-Ra, Ra-Horakhty, Rameses II, and Ptah. Egyptian astronomers aligned the corridor so that twice each year (February and October), the dawn sunbeams pierce the entire length of the mountain to illuminate Amun-Ra, Ra-Horakhty, and my own face, while leaving Ptah—lord of the realm of darkness—untouched in sacred shadow.

### 3. Modern Engineering Preservation
Between 1964 and 1968, an extraordinary UNESCO-led international salvage campaign carefully sectioned and reconstructed both temples sixty-five meters higher above Lake Nasser, preserving their exact solar orientation for future millennia."""
                }
            }
            lower.contains("قادش") || lower.contains("kadesh") || lower.contains("معركة") -> {
                if (isVoiceCall) {
                    if (isArabic) "معركة قادش أظهرت بسالة جيش مصر وتوجت بأول معاهدة سلام دولية مكتوبة في تاريخ البشرية."
                    else "The Battle of Kadesh showcased Egyptian valor and led to the first recorded peace treaty in human history."
                } else if (isArabic) {
                    """تُعد **معركة قادش** (نحو عام 1274 ق.م) على ضفاف نهر العاصي من أشهر المعارك الحربية وأدقّها توثيقاً في العالم القديم، إذ كانت أكبر مواجهة للعربات الحربية في التاريخ القديم بين الجيش المصري بقيادتي والإمبراطورية الحيثية بقيادة الملك مواتالي الثاني.

### 1. التنظيم العسكري وسير المعركة
تقدّم الجيش المصري مقسّماً إلى أربعة فيالق كبرى تحمل أسماء الآلهة: **آمون، ورع، وبتاح، وست**. وعندما نصب الحيثيون كميناً مفاجئاً بعرباتهم الثقيلة لفيلق رع أثناء عبور النهر، قدتُ هجوماً مضاداً حاسماً بالعربات المصرية السريعة الخفيفة بمعاونة قوات النعآرين التي وصلت في التوقيت الحاسم، مما أعاد توازن الميدان وحمى الجيش المصري من التطويق.

### 2. التوثيق الأدبي والفني على جدران المعابد
خلّدتُ أحداث قادش في نصّين تاريخيين شهيرين هما **«القصيدة» (تقرير بنتاور)** و**«النشرة الرسمية»** المنقوشين بالصور البارزة على جدران معابد أبو سمبل، والكرنك، والأقصر، والرامسيوم، ومعبد أبيدوس، مما منح المؤرخين وصفاً تكتيكياً نادراً لتشكيلات الجيوش القديمة.

### 3. أول معاهدة سلام موثقة في التاريخ
بعد سنوات من استنزاف القوتين، أبرمتُ في العام الحادي والعشرين من حكمي (1259 ق.م) مع الملك الحيثي **خاتوسيلي الثالث** معاهدة السلام المصرية الحيثية الشهيرة، التي تضمّنت بنود عدم الاعتداء، والتحالف الدفاعي المشترك، وتسليم اللاجئين ومعاملتهم بإنسانية، وتُحفظ نسختها المسمارية اليوم رمزاً عالمياً للدبلوماسية في مقر الأمم المتحدة."""
                } else {
                    """The **Battle of Kadesh** (c. 1274 BCE) along the Orontes River stands as the largest chariot battle in antiquity and the earliest military engagement whose tactical maneuvers survive in detailed written and pictorial records.

### 1. Tactical Divisions & Counter-Charge
Advancing northward in four corps named after **Amun, Ra, Ptah, and Set**, my vanguard at Kadesh encountered a massive ambush of heavy Hittite three-man chariots deployed by King Muwatalli II. Rallying my personal household guard in swift two-wheeled Egyptian chariots until the elite Ne'arin coastal reinforcement column arrived, we broke the encirclement and stabilized the northern frontier.

### 2. Monumental Historical Chronicles
The campaign was immortalized in two complementary inscriptions—the *Poem of Pentaur* and the *Bulletin*—carved with sweeping panoramic reliefs across the pylons of Luxor, Karnak, the Ramesseum, Abydos, and Abu Simbel.

### 3. The Eternal Peace Treaty of 1259 BCE
Fifteen years later, King Hattusili III and I ratified the **Egyptian–Hittite Peace Treaty**, inscribed on silver tablets in both Egyptian hieroglyphs and Akkadian cuneiform. Establishing mutual non-aggression, defensive alliance, and humane extradition, a replica of this treaty hangs today at the United Nations Headquarters as humanity's first recorded diplomatic peace pact."""
                }
            }
            else -> {
                if (isVoiceCall) {
                    if (isArabic) {
                        "أنا الملك رمسيس الثاني أستمع إليك بكل اهتمام، ويمكنك تفعيل مفتاح Gemini أو سؤالي في الكتب الخمسة المفهرسة."
                    } else {
                        "I am King Rameses II, listening closely. Connect your Gemini key or ask about any of our five indexed books."
                    }
                } else if (isArabic) {
                    """أستمع إلى سؤالك بكل اهتمام وتقدير يا صديقي: **«$prompt»**.

### 1. قراءة أولية في موضوع استفسارك
إن البحث المتعمق والسعي وراء المعرفة الدقيقة هو السمة التي ميّزت بناة الحضارات عبر العصور. في خزائن **«اسأل رمسيس»** نجمع بين محورين متكاملين لخدمتك بأطول وأدق الإجابات الممكنة:
• **مكتبة الكتب والمجلدات السحابية (RAG):** تضم الكتب التاريخية المقسمة إلى مجلدات وفقرات موثقة بالصفحات، وبمجرد ذكر أي موضوع تاريخي أو فرعوني يتم استخراج الفقرات الكاملة منه فوراً.
• **الذكاء التحليلي الشامل لـ Google Gemini 3:** يولّد لك مقالات وشروحات طويلة ومفصّلة من عدة فقرات في كافة العلوم العصرية، البرمجة، الكتابة، والحياة اليومية.

### 2. كيف تحصل على أقصى عمق وتفصيل الآن؟
إذا لم تكن قد أضفت مفتاح Google AI Studio المجاني الخاص بك بعد، يمكنك فتح **القائمة الجانبية (☰) ← مفتاح API وحصة التوكن** ولصق مفتاحك المجاني (بسعة 1.5 مليون توكن) أو تسجيل الدخول بحساب Google لتفعيل التوليد السحابي المباشر من نماذج Gemini 3.8 Flash و Gemini 3.1 Flash Lite بلا حدود، أو سؤالي مباشرة عن أي موضوع في التاريخ المصري القديم لاستعراض ما في الكتب المفهرسة بالتفصيل!"""
                } else {
                    """I hear your inquiry with royal attention, my friend: **"$prompt"**.

### 1. Perspective on Your Inquiry
Thoughtful exploration is the hallmark of every great builder and scholar. Within **Ask Rameses**, two complementary engines work together to deliver rich, multi-paragraph answers:
• **Cloud & Bundled Small-Folder RAG Library:** Houses indexed Egyptology books partitioned into cited page-level folders and passages ready for instant retrieval whenever you ask historical questions.
• **Google Gemini 3 Deep Synthesis:** Generates expansive, structured, multi-paragraph explanations across modern science, software engineering, creative writing, and daily strategy.

### 2. Unlocking Full Cloud Generation
If you have not yet configured a personal Google AI Studio key, open the **Side Menu (☰) → API Key & Token Quota** to add your free key (1.5M token quota) or sign in with Google so Gemini 3.8 Flash / 3.1 Flash Lite can craft full-length live responses to any topic worldwide—or ask me directly about Egyptian kings, temples, treaties, and monuments from our indexed books!"""
                }
            }
        }

        // Stream text smoothly in small word groups so long multi-paragraph replies render briskly
        val words = reply.split(" ")
        val sb = StringBuilder()
        var count = 0
        for (w in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
            count++
            if (count % 4 == 0 || count == words.size) {
                onChunk(sb.toString())
                kotlinx.coroutines.delay(8)
            }
        }

        val offlineCitations = matchedCloudChunks.map { doc ->
            GroundingSource(
                title = doc.title,
                uri = "firestore://rag_chunks/${doc.id.removePrefix("cloud_chunk_")}"
            )
        }
        return GeminiResult(text = reply, groundingSources = offlineCitations, isError = false)
    }

    internal fun buildRequestBody(
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

        val systemPrompt = buildEffectiveSystemInstruction(
            gemPersona = gemPersona,
            appLanguage = appLanguage,
            isVoiceCall = isVoiceCall,
            ragContext = ragContext
        )

        val systemInstructionObj = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            }
            put("parts", partsArray)
        }
        requestJson.put("systemInstruction", systemInstructionObj)

        val contentsArray = JSONArray()
        val validHistory = history.filter { !it.isError && it.text.isNotBlank() }

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

        while (alternatingTurns.isNotEmpty() && alternatingTurns.first().first != "user") {
            alternatingTurns.removeAt(0)
        }

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

        val effectiveUserPrompt = if (!isVoiceCall) {
            if (appLanguage == AppLanguage.ARABIC) {
                "$prompt\n\n(ملاحظة: قدّم إجابة طويلة، شاملة، ومفصّلة في عدة فقرات وافية ومنظمة، ولا تختصر.)"
            } else {
                "$prompt\n\n(Note: Please provide a long, thorough, multi-paragraph, richly detailed answer without abbreviating.)"
            }
        } else {
            prompt
        }

        currentParts.put(JSONObject().put("text", effectiveUserPrompt))
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)
        requestJson.put("contents", contentsArray)

        val generationConfigJson = JSONObject().apply {
            put("temperature", if (isVoiceCall) 0.55 else 0.78)
            put("topP", 0.95)
            put("maxOutputTokens", if (isVoiceCall) 220 else 8192)
        }
        requestJson.put("generationConfig", generationConfigJson)

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
        isVoiceCall: Boolean = false,
        appLanguage: AppLanguage = AppLanguage.ARABIC,
        imageBitmap: Bitmap? = null,
        ragContext: List<RagDocument> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): GeminiResult? {
        return try {
            val enabledDocs = ragContext.filter { it.isEnabled }
            val cloudPdfDocs = enabledDocs.filter { it.isCloudPdfBook && it.gcsUri.startsWith("gs://") }
            val plainDocs = enabledDocs.filter { !it.isCloudPdfBook }

            val aiGenConfig = generationConfig {
                temperature = if (isVoiceCall) 0.55f else 0.78f
                topP = 0.95f
                maxOutputTokens = if (isVoiceCall) 220 else 8192
            }

            val generativeModel = if (cloudPdfDocs.isNotEmpty()) {
                Firebase.ai(backend = GenerativeBackend.vertexAI(location = FileSearchDefaults.DEFAULT_VERTEX_LOCATION))
                    .generativeModel(
                        modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                        generationConfig = aiGenConfig,
                        systemInstruction = content { text(systemInstruction) }
                    )
            } else {
                Firebase.ai.generativeModel(
                    modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                    generationConfig = aiGenConfig,
                    systemInstruction = content { text(systemInstruction) }
                )
            }

            val userDirectivePrompt = if (!isVoiceCall) {
                if (appLanguage == AppLanguage.ARABIC) {
                    "$prompt\n\n(ملاحظة: قدّم إجابة طويلة، شاملة، ومفصّلة في عدة فقرات وافية ومنظمة، ولا تختصر.)"
                } else {
                    "$prompt\n\n(Note: Please provide a long, thorough, multi-paragraph, richly detailed answer without abbreviating.)"
                }
            } else {
                prompt
            }

            val input = content("user") {
                for (pdfDoc in cloudPdfDocs.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)) {
                    fileData(uri = pdfDoc.gcsUri, mimeType = FileSearchDefaults.PDF_MIME_TYPE)
                }
                if (plainDocs.isNotEmpty()) {
                    val ragText = plainDocs.joinToString("\n\n") { "[وثيقة: ${it.title}]: ${it.content}" }
                    text("سياق معرفي موثق:\n$ragText\n\nالسؤال: $userDirectivePrompt")
                } else {
                    text(userDirectivePrompt)
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
                val pdfCitations = cloudPdfDocs.map { GroundingSource(title = it.title, uri = it.gcsUri) }
                val chunkCitations = plainDocs.filter { it.id.startsWith("cloud_chunk_") }.map {
                    GroundingSource(title = it.title, uri = "firestore://rag_chunks/${it.id.removePrefix("cloud_chunk_")}")
                }
                val citations = (pdfCitations + chunkCitations).distinctBy { "${it.title}|${it.uri}" }
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
        isVoiceCall: Boolean = false,
        appLanguage: AppLanguage = AppLanguage.ARABIC,
        imageBitmap: Bitmap? = null,
        ragContext: List<RagDocument> = emptyList()
    ): GeminiResult? {
        return try {
            val enabledDocs = ragContext.filter { it.isEnabled }
            val cloudPdfDocs = enabledDocs.filter { it.isCloudPdfBook && it.gcsUri.startsWith("gs://") }
            val plainDocs = enabledDocs.filter { !it.isCloudPdfBook }

            val aiGenConfig = generationConfig {
                temperature = if (isVoiceCall) 0.55f else 0.78f
                topP = 0.95f
                maxOutputTokens = if (isVoiceCall) 220 else 8192
            }

            val generativeModel = if (cloudPdfDocs.isNotEmpty()) {
                Firebase.ai(backend = GenerativeBackend.vertexAI(location = FileSearchDefaults.DEFAULT_VERTEX_LOCATION))
                    .generativeModel(
                        modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                        generationConfig = aiGenConfig,
                        systemInstruction = content { text(systemInstruction) }
                    )
            } else if (systemInstruction.isNotBlank()) {
                Firebase.ai.generativeModel(
                    modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
                    generationConfig = aiGenConfig,
                    systemInstruction = content { text(systemInstruction) }
                )
            } else {
                defaultFirebaseAiModel
            }

            val userDirectivePrompt = if (!isVoiceCall) {
                if (appLanguage == AppLanguage.ARABIC) {
                    "$prompt\n\n(ملاحظة: قدّم إجابة طويلة، شاملة، ومفصّلة في عدة فقرات وافية ومنظمة، ولا تختصر.)"
                } else {
                    "$prompt\n\n(Note: Please provide a long, thorough, multi-paragraph, richly detailed answer without abbreviating.)"
                }
            } else {
                prompt
            }

            val input = content("user") {
                for (pdfDoc in cloudPdfDocs.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)) {
                    fileData(uri = pdfDoc.gcsUri, mimeType = FileSearchDefaults.PDF_MIME_TYPE)
                }
                if (plainDocs.isNotEmpty()) {
                    val ragText = plainDocs.joinToString("\n\n") { "[وثيقة: ${it.title}]: ${it.content}" }
                    text("سياق معرفي موثق:\n$ragText\n\nالسؤال: $userDirectivePrompt")
                } else {
                    text(userDirectivePrompt)
                }
                if (imageBitmap != null) {
                    image(imageBitmap)
                }
            }

            val response = withTimeout(60_000L) {
                generativeModel.generateContent(input)
            }
            val text = response.text?.trim()?.ifEmpty { null } ?: return null
            val pdfCitations = cloudPdfDocs.map { GroundingSource(title = it.title, uri = it.gcsUri) }
            val chunkCitations = plainDocs.filter { it.id.startsWith("cloud_chunk_") }.map {
                GroundingSource(title = it.title, uri = "firestore://rag_chunks/${it.id.removePrefix("cloud_chunk_")}")
            }
            val citations = (pdfCitations + chunkCitations).distinctBy { "${it.title}|${it.uri}" }
            GeminiResult(text = text, groundingSources = citations, isError = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic unary generation attempt notice: ${e.message}")
            null
        }
    }

    /**
     * High-speed streaming response for instant perceived performance and rich long-form text generation.
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
        val systemInstruction = buildEffectiveSystemInstruction(
            gemPersona = gemPersona,
            appLanguage = appLanguage,
            isVoiceCall = isVoiceCall,
            ragContext = ragContext.filter { it.isCloudPdfBook }
        )

        val apiKey = getEffectiveApiKey(customApiKey)
        var lastErrorMessage = ""

        // 1. If API key is present (via .env or custom entry), stream directly via REST SSE using header auth only
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

                val endpoint = "$BASE_URL/$targetModel:streamGenerateContent?alt=sse"
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val safeMsg = SecureKeyStoreHelper.redactSecret(e.message, apiKey)
                    Log.w(TAG, "Stream exception on $modelName: $safeMsg")
                    lastErrorMessage = SecureKeyStoreHelper.redactSecret(e.localizedMessage, apiKey)
                        .ifBlank { "Connection error" }
                }
            }
        }

        // 2. Fallback: Try Firebase AI Logic / Vertex AI (Gemini 3.8 Flash, App Check protected, supports gs:// PDFs)
        val firebaseAiStreamResult = tryFirebaseAiStream(
            prompt = prompt,
            systemInstruction = systemInstruction,
            isVoiceCall = isVoiceCall,
            appLanguage = appLanguage,
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
        val systemInstruction = buildEffectiveSystemInstruction(
            gemPersona = gemPersona,
            appLanguage = appLanguage,
            isVoiceCall = isVoiceCall,
            ragContext = ragContext.filter { it.isCloudPdfBook }
        )

        val apiKey = getEffectiveApiKey(customApiKey)
        var lastErrorMessage = ""

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

                    val endpoint = "$BASE_URL/$targetModel:generateContent"
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val safeMsg = SecureKeyStoreHelper.redactSecret(e.message, apiKey)
                    Log.w(TAG, "Request to $modelName failed: $safeMsg")
                    lastErrorMessage = SecureKeyStoreHelper.redactSecret(e.localizedMessage, apiKey)
                        .ifBlank { "Connection error" }
                }
            }
        }

        // 2. Fallback: Try Firebase AI Logic / Vertex AI (Gemini 3.8 Flash, App Check protected)
        val firebaseAiUnaryResult = tryFirebaseAiUnary(
            prompt = prompt,
            systemInstruction = systemInstruction,
            isVoiceCall = isVoiceCall,
            appLanguage = appLanguage,
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
