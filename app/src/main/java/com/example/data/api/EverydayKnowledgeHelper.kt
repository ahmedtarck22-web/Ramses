package com.example.data.api

import android.os.Build
import com.example.data.model.AppLanguage
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Real-time clock, calendar, live keyless weather (Open-Meteo), live Wikipedia encyclopedia lookup,
 * and rich conversational/everyday/cartoon/tech responder so both Ramses & Gemini answer natural
 * daily questions accurately at all times.
 */
object EverydayKnowledgeHelper {

    private val fastHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun isRobolectricRuntime(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            runCatching {
                Class.forName("org.robolectric.Robolectric")
                true
            }.getOrDefault(false)
    }

    /**
     * Normalizes Arabic letters (hamzas, taa marbuta, alef maqsura, tashkeel, punctuation)
     * so colloquial queries like "إزيك", "إزاي", "عامل إيه؟", "النهاردة إيه" match reliably.
     */
    fun normalizeArabic(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "") // tashkeel & tatweel
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(Regex("[؟?!.,،؛:\"'«»()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildCurrentDateTimeContext(appLanguage: AppLanguage): String {
        return try {
            val now = ZonedDateTime.now()
            val arLocale = Locale.forLanguageTag("ar-EG")
            val enLocale = Locale.US

            val dayAr = now.format(DateTimeFormatter.ofPattern("EEEE", arLocale))
            val dateAr = now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", arLocale))
            val timeAr = now.format(DateTimeFormatter.ofPattern("hh:mm a", arLocale))

            val dayEn = now.format(DateTimeFormatter.ofPattern("EEEE", enLocale))
            val dateEn = now.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", enLocale))
            val timeEn = now.format(DateTimeFormatter.ofPattern("hh:mm a (HH:mm)", enLocale))

            val hijriStr = runCatching {
                val hijri = HijrahDate.from(now.toLocalDate())
                val hijriFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", arLocale)
                hijri.format(hijriFormatter)
            }.getOrDefault("")

            val tzId = TimeZone.getDefault().id
            if (appLanguage == AppLanguage.ARABIC) {
                buildString {
                    append("[معلومات الوقت والتاريخ الحقيقية الآن في جهاز المستخدم: ")
                    append("اليوم: $dayAr | التاريخ الميلادي: $dateAr")
                    if (hijriStr.isNotBlank()) append(" | التاريخ الهجري: $hijriStr هـ")
                    append(" | الساعة الآن: $timeAr | المنطقة الزمنية: $tzId]")
                }
            } else {
                "[Real-Time User Device Clock: Day: $dayEn | Date: $dateEn | Current Local Time: $timeEn | TimeZone: $tzId]"
            }
        } catch (_: Throwable) {
            val sdf = SimpleDateFormat("EEEE, yyyy-MM-dd hh:mm a", Locale.US)
            "[Current Device Date & Time: ${sdf.format(Date())}]"
        }
    }

    fun isGreetingOrSmallTalkQuery(prompt: String): Boolean {
        val norm = normalizeArabic(prompt)
        if (norm.isBlank()) return false
        // If user also asks a concrete topic (e.g., "ازيك قولي التاريخ" or "ازيك اشرح كود"), let topic win if long
        val greetingPhrases = listOf(
            "ازيك", "ازاي الحال", "ازاي الصحه", "عامل ايه", "عامله ايه", "اخبارك",
            "اخبارك ايه", "كيف حالك", "كيفك", "شلونك", "صباح الخير", "صباح النور",
            "مساء الخير", "مساء النور", "السلام عليكم", "سلام عليكم", "مرحبا", "اهلا",
            "اهلين", "هاي", "هلا", "حبيبي", "ربنا يخليك", "شكرا", "متشكر", "تسلم",
            "يسعد صباحك", "يسعد مساك", "وحشتني",
            "how are you", "how r u", "how's it going", "whats up", "what's up",
            "good morning", "good evening", "hello", "hey gemini", "hey ramses", "thank you", "thanks"
        )
        val exactShortTriggers = setOf("ازاي", "عامل", "تمام", "الحمد لله", "hi", "hey", "yo")
        if (norm in exactShortTriggers) return true
        val words = norm.split(" ")
        if (words.size <= 7 && greetingPhrases.any { norm.contains(it) }) {
            // Check it doesn't simultaneously ask for weather, date, code, or cartoon
            if (!isDateTimeQuery(prompt) && !isWeatherQuery(prompt) && !isCartoonOrMovieQuery(prompt)) {
                return true
            }
        }
        return false
    }

    fun isDateTimeQuery(prompt: String): Boolean {
        val norm = normalizeArabic(prompt)
        val triggers = listOf(
            "التاريخ", "تاريخ اليوم", "تاريخ النهارده", "النهارده ايه", "النهارده كام",
            "اليوم ايه", "اي يوم", "احنا في يوم ايه", "احنا في شهر ايه", "احنا في سنه كام",
            "الساعه كام", "الساعه كم", "الوقت كام", "كم الساعه", "الوقت الان", "الوقت دلوقتي",
            "توقيت", "ساعه الان", "يوم ايه النهارده",
            "what time", "current time", "what day is it", "today's date", "what is the date",
            "what date is today", "day of the week"
        )
        if (triggers.any { norm.contains(it) }) return true
        val singleWords = setOf("الوقت", "الساعه", "اليوم", "النهارده", "date", "time", "today")
        return norm in singleWords
    }

    fun isWeatherQuery(prompt: String): Boolean {
        val norm = normalizeArabic(prompt)
        val triggers = listOf(
            "الجو", "الطقس", "درجه الحراره", "درجات الحراره", "الحراره كام",
            "الدنيا حر", "الدنيا برد", "مطر", "امطار", "ارصاد", "حاله الجو", "حاله الطقس",
            "اخبار الجو", "اخبار الطقس",
            "weather", "temperature", "forecast", "is it raining", "how hot", "how cold"
        )
        return triggers.any { norm.contains(it) }
    }

    fun isCartoonOrMovieQuery(prompt: String): Boolean {
        val norm = normalizeArabic(prompt)
        val triggers = listOf(
            "كرتون", "كارتون", "فيلم", "افلام", "انمي", "انيميشن", "رسوم متحركه",
            "ديزني", "بيكسار", "توم وجيري", "سبونج بوب", "الاسد الملك", "سيمبا",
            "توي ستوري", "نيمو", "الفار الطباخ", "راتاتوي", "شركه المرعبين", "فروزن",
            "موانا", "انكانتو", "كونان", "المحقق كونان", "ناروتو", "ون بيس", "دراغون بول",
            "غيبلي", "شريك", "كونغ فو باندا", "عائله الخارقين", "اب ", "وول اي",
            "cartoon", "anime", "animation", "animated movie", "disney", "pixar",
            "lion king", "toy story", "finding nemo", "ratatouille", "spongebob",
            "tom and jerry", "frozen", "spirited away", "shrek", "kung fu panda", "movie"
        )
        return triggers.any { norm.contains(it) }
    }

    fun buildDirectDateTimeAnswer(
        prompt: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean,
        gemPersona: String = "GEMINI_DEFAULT"
    ): String {
        val now = ZonedDateTime.now()
        val arLocale = Locale.forLanguageTag("ar-EG")
        val enLocale = Locale.US

        val dayAr = now.format(DateTimeFormatter.ofPattern("EEEE", arLocale))
        val dateAr = now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", arLocale))
        val timeAr = now.format(DateTimeFormatter.ofPattern("hh:mm a", arLocale))
        val time24 = now.format(DateTimeFormatter.ofPattern("HH:mm", enLocale))

        val dayEn = now.format(DateTimeFormatter.ofPattern("EEEE", enLocale))
        val dateEn = now.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", enLocale))
        val timeEn = now.format(DateTimeFormatter.ofPattern("hh:mm a", enLocale))

        val hijriStr = runCatching {
            val hijri = HijrahDate.from(now.toLocalDate())
            hijri.format(DateTimeFormatter.ofPattern("d MMMM yyyy", arLocale))
        }.getOrDefault("")

        return if (appLanguage == AppLanguage.ARABIC) {
            if (isVoiceCall) {
                val hijriPart = if (hijriStr.isNotBlank()) "، الموافق $hijriStr هجرياً" else ""
                "اليوم هو $dayAr، $dateAr ميلادياً$hijriPart، والساعة الآن $timeAr."
            } else {
                buildString {
                    append("🕒 **الوقت والتاريخ الآن بدقة:**\n\n")
                    append("• **اليوم:** $dayAr\n")
                    append("• **التاريخ الميلادي:** $dateAr م\n")
                    if (hijriStr.isNotBlank()) {
                        append("• **التاريخ الهجري:** $hijriStr هـ\n")
                    }
                    append("• **الساعة الآن:** $timeAr (بنظام 24 ساعة: $time24)\n\n")
                    if (gemPersona == "RAMSES") {
                        append("أتمنى لك يوماً مباركاً مليئاً بالإنجاز والنشاط يا صديقي!")
                    } else {
                        append("أتمنى لك يوماً سعيداً وموفقاً! هل تحب أن أساعدك في تنظيم جدولك أو معرفة حالة الطقس اليوم؟")
                    }
                }
            }
        } else {
            if (isVoiceCall) {
                "Today is $dayEn, $dateEn, and the current time is $timeEn."
            } else {
                buildString {
                    append("🕒 **Current Date & Local Time:**\n\n")
                    append("• **Day:** $dayEn\n")
                    append("• **Date:** $dateEn\n")
                    append("• **Current Time:** $timeEn ($time24)\n\n")
                    append("Wishing you a productive and wonderful day! Let me know if you'd like today's weather or help planning anything.")
                }
            }
        }
    }

    private data class GeoTarget(val cityAr: String, val cityEn: String, val lat: Double, val lon: Double)

    private fun resolveTargetCoordinates(prompt: String): GeoTarget {
        val norm = normalizeArabic(prompt)
        val knownCities = listOf(
            Triple("الاسكندريه|اسكندريه|alexandria", GeoTarget("الإسكندرية", "Alexandria", 31.2001, 29.9187), ""),
            Triple("الجيزه|giza", GeoTarget("الجيزة", "Giza", 30.0131, 31.2089), ""),
            Triple("اسوان|aswan", GeoTarget("أسوان", "Aswan", 24.0889, 32.8998), ""),
            Triple("الاقصر|اقصر|luxor", GeoTarget("الأقصر", "Luxor", 25.6872, 32.6396), ""),
            Triple("شرم الشيخ|sharm", GeoTarget("شرم الشيخ", "Sharm El-Sheikh", 27.9158, 34.3300), ""),
            Triple("الغردقه|hurghada", GeoTarget("الغردقة", "Hurghada", 27.2579, 33.8116), ""),
            Triple("المنصوره|mansoura", GeoTarget("المنصورة", "Mansoura", 31.0409, 31.3785), ""),
            Triple("طنطا|tanta", GeoTarget("طنطا", "Tanta", 30.7865, 31.0004), ""),
            Triple("بورسعيد|port said", GeoTarget("بورسعيد", "Port Said", 31.2653, 32.3019), ""),
            Triple("السويس|suez", GeoTarget("السويس", "Suez", 29.9668, 32.5498), ""),
            Triple("اسيوط|asyut", GeoTarget("أسيوط", "Asyut", 27.1810, 31.1837), ""),
            Triple("الرياض|riyadh", GeoTarget("الرياض", "Riyadh", 24.7136, 46.6753), ""),
            Triple("جده|jeddah", GeoTarget("جدة", "Jeddah", 21.4858, 39.1925), ""),
            Triple("مكه|mecca|makkah", GeoTarget("مكة المكرمة", "Makkah", 21.3891, 39.8579), ""),
            Triple("دبي|dubai", GeoTarget("دبي", "Dubai", 25.2048, 55.2708), ""),
            Triple("ابوظبي|ابو ظبي|abu dhabi", GeoTarget("أبوظبي", "Abu Dhabi", 24.4539, 54.3773), ""),
            Triple("الكويت|kuwait", GeoTarget("الكويت", "Kuwait City", 29.3759, 47.9774), ""),
            Triple("الدوحه|قطر|doha", GeoTarget("الدوحة", "Doha", 25.2854, 51.5310), ""),
            Triple("عمان|amman", GeoTarget("عمّان", "Amman", 31.9454, 35.9284), ""),
            Triple("بيروت|beirut", GeoTarget("بيروت", "Beirut", 33.8938, 35.5018), ""),
            Triple("بغداد|baghdad", GeoTarget("بغداد", "Baghdad", 33.3152, 44.3661), ""),
            Triple("لندن|london", GeoTarget("لندن", "London", 51.5072, -0.1276), ""),
            Triple("باريس|paris", GeoTarget("باريس", "Paris", 48.8566, 2.3522), ""),
            Triple("نيويورك|new york", GeoTarget("نيويورك", "New York", 40.7128, -74.0060), ""),
            Triple("القاهره|مصر|cairo|egypt", GeoTarget("القاهرة", "Cairo", 30.0444, 31.2357), "")
        )
        for ((pattern, target, _) in knownCities) {
            if (Regex(pattern).containsMatchIn(norm)) return target
        }

        val tz = TimeZone.getDefault().id.lowercase(Locale.ROOT)
        return when {
            tz.contains("riyadh") -> GeoTarget("الرياض", "Riyadh", 24.7136, 46.6753)
            tz.contains("dubai") -> GeoTarget("دبي", "Dubai", 25.2048, 55.2708)
            tz.contains("kuwait") -> GeoTarget("الكويت", "Kuwait City", 29.3759, 47.9774)
            tz.contains("amman") -> GeoTarget("عمّان", "Amman", 31.9454, 35.9284)
            tz.contains("baghdad") -> GeoTarget("بغداد", "Baghdad", 33.3152, 44.3661)
            tz.contains("casablanca") -> GeoTarget("الدار البيضاء", "Casablanca", 33.5731, -7.5898)
            tz.contains("london") -> GeoTarget("لندن", "London", 51.5072, -0.1276)
            else -> GeoTarget("القاهرة", "Cairo", 30.0444, 31.2357)
        }
    }

    private fun describeWeatherCode(code: Int, isArabic: Boolean): String {
        return when (code) {
            0 -> if (isArabic) "صافي ومشمس ☀️" else "Clear sky ☀️"
            1, 2 -> if (isArabic) "غائم جزئياً ولطيف ⛅" else "Partly cloudy ⛅"
            3 -> if (isArabic) "غائم ☁️" else "Overcast ☁️"
            45, 48 -> if (isArabic) "شبورة أو ضباب خفيف 🌫️" else "Foggy 🌫️"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> if (isArabic) "أمطار متفرقة 🌧️" else "Rain showers 🌧️"
            71, 73, 75 -> if (isArabic) "ثلوج ❄️" else "Snowfall ❄️"
            95, 96, 99 -> if (isArabic) "عواصف رعدية ⛈️" else "Thunderstorm ⛈️"
            else -> if (isArabic) "معتدل ومستقر 🌤️" else "Mild & pleasant 🌤️"
        }
    }

    /**
     * Fetches real live weather from Open-Meteo (zero API key required).
     */
    fun fetchLiveWeatherSummary(
        prompt: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean = false
    ): String? {
        if (isRobolectricRuntime()) return null
        return try {
            val target = resolveTargetCoordinates(prompt)
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${target.lat}&longitude=${target.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AskRamesesAndroid/1.0")
                .get()
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return null

                val json = JSONObject(body)
                val current = json.optJSONObject("current") ?: return null
                val temp = current.optDouble("temperature_2m", Double.NaN)
                if (temp.isNaN()) return null
                val feelsLike = current.optDouble("apparent_temperature", temp)
                val humidity = current.optInt("relative_humidity_2m", 50)
                val wind = current.optDouble("wind_speed_10m", 10.0)
                val code = current.optInt("weather_code", 0)

                val daily = json.optJSONObject("daily")
                val maxTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
                val minTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp

                val isArabic = appLanguage == AppLanguage.ARABIC
                val cond = describeWeatherCode(code, isArabic)
                val tRound = temp.toInt()
                val fRound = feelsLike.toInt()
                val hiRound = maxTemp.toInt()
                val loRound = minTemp.toInt()
                val wRound = wind.toInt()

                if (isArabic) {
                    if (isVoiceCall) {
                        "الجو الآن في ${target.cityAr} ${cond.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()}، ودرجة الحرارة الحالية $tRound درجة مئوية، والعظمى اليوم $hiRound والصغرى $loRound درجة."
                    } else {
                        """🌤️ **حالة الجو والطقس الآن في ${target.cityAr}:**

• **الحالة العامة:** $cond
• **درجة الحرارة الحالية:** **$tRound°C** (تُحسّ كأنها $fRound°C)
• **العظمى / الصغرى اليوم:** $hiRound°C / $loRound°C
• **نسبة الرطوبة:** $humidity%
• **سرعة الرياح:** $wRound كم/س

💡 يمكنك أيضاً ذكر اسم أي مدينة أخرى (مثل: *«الجو في الإسكندرية»* أو *«الطقس في أسوان»* أو *«الطقس في الرياض»*) وسأجلب لك درجات الحرارة الحية فوراً!"""
                    }
                } else {
                    if (isVoiceCall) {
                        "Right now in ${target.cityEn}, it is $tRound degrees Celsius with ${cond.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()}, a high of $hiRound and a low of $loRound."
                    } else {
                        """🌤️ **Live Weather in ${target.cityEn}:**

• **Condition:** $cond
• **Current Temperature:** **$tRound°C** (Feels like $fRound°C)
• **Today's High / Low:** $hiRound°C / $loRound°C
• **Humidity:** $humidity%
• **Wind Speed:** $wRound km/h

💡 Ask about any city (e.g., *"Weather in Alexandria"* or *"Weather in Dubai"*) for instant live temperatures!"""
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fetches a live encyclopedia summary from Arabic or English Wikipedia for general,
     * cartoon/movie, scientific, or cultural questions when no Gemini API key is configured.
     */
    fun fetchLiveWikipediaSummary(
        prompt: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean
    ): String? {
        if (isRobolectricRuntime()) return null
        val cleanedQuery = prompt
            .replace(Regex("(?i)^(من هو|من هي|ما هو|ما هي|كلمني عن|حدثني عن|اشرح لي|ايه هو|يعني ايه|معلومات عن|قصة فيلم|قصة كرتون|فيلم كرتون|كرتون|what is|who is|tell me about)\\s+"), "")
            .replace(Regex("[؟?!.,،]"), "")
            .trim()
        if (cleanedQuery.length < 2) return null

        val primaryLang = if (appLanguage == AppLanguage.ARABIC) "ar" else "en"
        val secondaryLang = if (primaryLang == "ar") "en" else "ar"

        return queryWikipediaLang(cleanedQuery, primaryLang, appLanguage, isVoiceCall)
            ?: queryWikipediaLang(cleanedQuery, secondaryLang, appLanguage, isVoiceCall)
    }

    private fun queryWikipediaLang(
        searchTerm: String,
        wikiLang: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean
    ): String? {
        return try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val apiUrl = "https://$wikiLang.wikipedia.org/w/api.php" +
                "?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1" +
                "&prop=extracts&exintro=1&explaintext=1&format=json"

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "AskRamesesAndroid/1.0")
                .get()
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return null

                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                if (!keys.hasNext()) return null
                val firstPage = pages.optJSONObject(keys.next()) ?: return null
                val title = firstPage.optString("title").trim()
                val extract = firstPage.optString("extract").trim()
                if (extract.length < 45) return null

                if (isVoiceCall) {
                    val sentences = extract.split(Regex("(?<=[.؟!])\\s+")).take(2).joinToString(" ")
                    sentences.take(280)
                } else {
                    if (appLanguage == AppLanguage.ARABIC) {
                        "✨ **$title**\n\n$extract"
                    } else {
                        "✨ **$title**\n\n$extract"
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Rich, natural, multi-domain responder for greetings, date/day/time, weather,
     * cartoon movies, technology/coding/Blender, and general everyday questions.
     */
    fun generateNaturalEverydayResponse(
        prompt: String,
        appLanguage: AppLanguage,
        isVoiceCall: Boolean,
        gemPersona: String = "GEMINI_DEFAULT"
    ): String {
        val norm = normalizeArabic(prompt)
        val isArabic = appLanguage == AppLanguage.ARABIC
        val isRamses = gemPersona == "RAMSES"

        // 1. Real-Time Date, Day of Week & Clock Time
        if (isDateTimeQuery(prompt)) {
            return buildDirectDateTimeAnswer(prompt, appLanguage, isVoiceCall, gemPersona)
        }

        // 2. Real Live Weather (Open-Meteo + graceful offline note)
        if (isWeatherQuery(prompt)) {
            fetchLiveWeatherSummary(prompt, appLanguage, isVoiceCall)?.let { return it }
            return if (isArabic) {
                if (isVoiceCall) {
                    "الأجواء اليوم معتدلة ولطيفة بوجه عام، وتأكد من اتصالك بالإنترنت لعرض درجة الحرارة اللحظية لمدينتك."
                } else {
                    "🌤️ **حالة الطقس اليوم:**\nالأجواء معتدلة ولطيفة بوجه عام. بمجرد توفر اتصال بالإنترنت سأعرض لك درجة الحرارة اللحظية، الرطوبة، وسرعة الرياح لمدينتك مباشرة!"
                }
            } else {
                "🌤️ **Weather Update:** Conditions are generally mild today. Connect to the internet to fetch live temperature, humidity, and wind speed for your city!"
            }
        }

        // 3. Natural Everyday Greetings, How-Are-You, Thanks & Small Talk
        if (isGreetingOrSmallTalkQuery(prompt)) {
            if (norm.contains("شكرا") || norm.contains("متشكر") || norm.contains("تسلم") || norm.contains("thank")) {
                return if (isArabic) {
                    if (isRamses) "على الرحب والسعة يا صديقي العزيز! يسعدني دائماً محاورتك ومساعدتك في أي سؤال تاريخي أو علمي أو يومي."
                    else "عفواً على الرحب والسعة! سعيد جداً بمساعدتك، وأنا جاهز لأي سؤال آخر في أي وقت."
                } else {
                    "You're very welcome! Always happy to help with anything you need."
                }
            }
            if (norm.contains("صباح")) {
                return if (isArabic) {
                    if (isRamses) "صباح الخير والإشراق يا صديقي! أتمنى لك يوماً ساطعاً كشمس طيبة وأبو سمبل، تفضّل بسؤالك في أي موضوع تحب."
                    else "صباح النور والسرور! أتمنى لك يوماً جميلاً ومليئاً بالنشاط. كيف يمكنني مساعدتك اليوم؟"
                } else "Good morning! Wishing you a bright and productive day. How can I help you?"
            }
            if (norm.contains("مساء")) {
                return if (isArabic) {
                    if (isRamses) "مساء الخير والسكينة يا صديقي العزيز! أهلاً بك، اسألني في التاريخ أو التكنولوجيا أو الطقس أو أي موضوع يخطر ببالك."
                    else "مساء النور وأهلاً بك! كيف يمكنني مساعدتك هذا المساء؟"
                } else "Good evening! How can I assist you tonight?"
            }

            return if (isArabic) {
                if (isVoiceCall) {
                    if (isRamses) {
                        "أهلاً بك يا صديقي! أنا بخير وسعادة بلقائك، اسألني في التاريخ أو الوقت أو الطقس أو التكنولوجيا أو الأفلام وسأجيبك فوراً."
                    } else {
                        "الحمد لله أنا بخير وجاهز تماماً لمساعدتك! تفضّل واسألني في أي موضوع تحب."
                    }
                } else if (isRamses) {
                    """أهلاً وسهلاً بك يا صديقي العزيز! الحمد لله أنا في أتم الخير والبهجة بمحاورتك اليوم 👑

يمكنك التحدث معي بحرية تامة في **أي موضوع يخطر ببالك**:
• **الأسئلة اليومية السريعة:** اسألني عن **الوقت، التاريخ، اليوم، أو حالة الجو والطقس** في أي مدينة.
• **التكنولوجيا والبرمجة وBlender:** كتابة الأكواد، تطوير التطبيقات، والتصميم ثلاثي الأبعاد.
• **الأفلام، الكرتون، والثقافة العامة:** ترشيحات وقصص أفلام الكرتون والأنيميشن والعلوم والرياضة.
• **التاريخ والحضارة المصرية:** أسرار رمسيس الثاني، معبد أبو سمبل، قادش، والكتب الموثقة.

تفضّل يا صديقي، عمّ تحب أن نتحدث الآن؟"""
                } else {
                    """الحمد لله أنا بخير وفي أتم الاستعداد لمساعدتك يا صديقي! ✨

اسألني بطبيعية تامة في أي مجال:
• **اليوم والوقت والطقس اللحظي** لأي مدينة.
• **البرمجة، الأكواد، والتصميم في Blender** والتقنيات الحديثة.
• **أفلام الكرتون والأنيميشن**، الثقافة العامة، الأفكار الإبداعية، أو التاريخ.

تفضّل، ما الذي يدور في ذهنك الآن؟"""
                }
            } else {
                if (isVoiceCall) {
                    "I'm doing great, thank you for asking! Ask me about the time, weather, tech, animated movies, history, or anything on your mind."
                } else {
                    "I'm doing great, thank you for asking! ✨ Feel free to ask me anything—today's date and time, live weather, coding & Blender, animated movies, general knowledge, or ancient history!"
                }
            }
        }

        // 4. Cartoon Movies, Animation & Entertainment
        if (isCartoonOrMovieQuery(prompt)) {
            fetchLiveWikipediaSummary(prompt, appLanguage, isVoiceCall)?.let { wikiText ->
                return wikiText
            }
            return buildCartoonKnowledgeAnswer(norm, isArabic, isVoiceCall)
        }

        // 5. Technology, Programming, Coding & Blender
        if (GeminiApiClient.isTechnicalOrSoftwareQuery(prompt)) {
            return buildTechnicalKnowledgeAnswer(prompt, norm, isArabic, isVoiceCall)
        }

        // 6. Jokes or Daily Wisdom / Advice
        if (norm.contains("نكته") || norm.contains("ضحكني") || norm.contains("joke")) {
            return if (isArabic) {
                "😄 مرة مبرمج دخل سوبر ماركت، زوجته قالتله: «هات علبة لبن، ولو لقيت بيض هات ستة».. رجع البيت ومعاه **6 علب لبن**! سألته ليه كده؟ قالها: «عشان لقيت بيض!» 😂"
            } else {
                "😄 Why do programmers prefer dark mode? Because light attracts bugs! 😂"
            }
        }
        if (norm.contains("نصيحه") || norm.contains("حكمه") || norm.contains("advice") || norm.contains("wisdom")) {
            return if (isArabic) {
                "🌟 **نصيحة اليوم:** ابدأ يومك بأهم خطوة صغيرة يمكنك إنجازها الآن ولا تنتظر اللحظة المثالية؛ فالصروح العظيمة—من معابد النيل الخالدة إلى أعظم الابتكارات الحديثة—بُنيت حجراً فوق حجر بالاستمرارية والهدوء."
            } else {
                "🌟 **Daily Wisdom:** Focus on consistent daily progress rather than waiting for perfection—every monumental achievement is built one deliberate step at a time."
            }
        }

        // 7. Live Wikipedia Lookup for any general topic (people, countries, science, sports, books)
        fetchLiveWikipediaSummary(prompt, appLanguage, isVoiceCall)?.let { return it }

        // 8. Warm, helpful conversational fallback
        return if (isArabic) {
            if (isVoiceCall) {
                "سؤال جميل! لتوليد شرح مخصص ومفصل أكثر حول هذا الموضوع، يمكنك تفعيل الإنترنت أو إضافة مفتاح Gemini الشخصي من القائمة الجانبية، أو سؤالي في الطقس والوقت والبرمجة والأفلام والتاريخ."
            } else {
                """أهلاً بك! سؤالك حول **«$prompt»** سؤال رائع.

• في الوضع الحالي أستطيع إجابتك فوراً ومباشرة على **الوقت والتاريخ واليوم**، **حالة الجو والطقس الحية**، **أسئلة البرمجة وBlender**، **أفلام الكرتون والأنيميشن**، **الموسوعة المعرفية الحية**، و**جميع موضوعات التاريخ المصري وكتب الـ RAG الخمسة**.
• وللحصول على حوار توليدي مفتوح غير محدود في أدق التفاصيل عبر **Gemini 3**، يمكنك أيضاً إضافة مفتاح Google AI Studio المجاني من **القائمة الجانبية (☰) ← مفتاح API الشخصي**."""
            }
        } else {
            """Great question about **"$prompt"**!

• I can immediately answer **current time/date/day**, **live city weather**, **coding & Blender workflows**, **animated movies**, **live encyclopedia topics**, and **all 5 indexed Egyptology books**.
• For unlimited open-ended generative reasoning via **Gemini 3**, you can also add a free Google AI Studio key under **Side Menu (☰) → Personal API Key**."""
        }
    }

    private fun buildCartoonKnowledgeAnswer(norm: String, isArabic: Boolean, isVoiceCall: Boolean): String {
        if (!isArabic) {
            return if (isVoiceCall) {
                "Some of the greatest animated movies ever made include The Lion King, Toy Story, Finding Nemo, Ratatouille, Inside Out, and Spirited Away!"
            } else {
                """🎬 **Top Beloved Animated & Cartoon Classics:**

1. **The Lion King (Disney):** Simba's timeless journey of courage, family, and reclaiming his rightful place in the Pride Lands.
2. **Toy Story (Pixar):** Woody and Buzz Lightyear's unforgettable story of friendship and adventure.
3. **Ratatouille (Pixar):** Remy, an ambitious young rat in Paris, proves that *"anyone can cook"* with passion and creativity.
4. **Inside Out 1 & 2 (Pixar):** A brilliant, heartwarming look inside human emotions—Joy, Sadness, Anxiety, and growing up.
5. **Spirited Away (Studio Ghibli):** Hayao Miyazaki's Oscar-winning masterpiece of imagination and bravery.

Tell me which cartoon or character you love most and I'll tell you all about its story!"""
            }
        }

        return when {
            norm.contains("الاسد الملك") || norm.contains("سيمبا") || norm.contains("lion king") -> {
                if (isVoiceCall) "فيلم الأسد الملك من أعظم أفلام ديزني، ويحكي رحلة الشبل سيمبا لاستعادة مملكته بشجاعة بعد رحيل والده الملك موفاسا وبمساعدة تيمون وبومبا."
                else """🦁 **فيلم الكرتون الخالد: الأسد الملك (The Lion King)**

يُعد أحد أعظم روائع **ديزني** في تاريخ الرسوم المتحركة (صدر عام 1994):
• **القصة:** تدور حول الشبل **«سيمبا»** ابن الملك الحكيم **«موفاسا»**، ومؤامرة عمه الماكر **«سكار»** للاستيلاء على أرض العزة، ثم نشأة سيمبا مع الثنائي المرح **تيمون وبومبا** على مبدأ *«هاكونا ماتاتا»* قبل أن يقرر العودة بشجاعة لاستعادة مملكته.
• **لماذا يُحبّه الجميع؟** يجمع بين الموسيقى الأسطورية لهانز زيمر، الكوميديا المحببة في الدبلجة العربية المصرية الشهيرة، ورسالة عميقة عن المسؤولية وتجاوز الماضي."""
            }
            norm.contains("توم وجيري") || norm.contains("tom and jerry") -> {
                if (isVoiceCall) "توم وجيري من أشهر سلاسل الكرتون الكلاسيكية في التاريخ، ابتكرها ويليام هانا وجوزيف باربيرا عام 1940 وفازت بسبع جوائز أوسكار."
                else """🐱🐭 **توم وجيري (Tom and Jerry)**

واحدة من أشهر وأمتع سلاسل الكرتون في تاريخ التلفزيون والسينما، ابتكرها **ويليام هانا وجوزيف باربيرا** عام **1940**:
• تعتمد براعتها على الكوميديا الحركية الصامتة والموسيقى الأوركسترالية المعبرة بدون الحاجة لحوار طويل، وقد حصدت السلسلة الأصلية **7 جوائز أوسكار** لأفضل فيلم رسوم متحركة قصير!"""
            }
            norm.contains("سبونج بوب") || norm.contains("spongebob") -> {
                if (isVoiceCall) "سبونج بوب سكوير بانتس مسلسل كرتوني كوميدي شهير تدور أحداثه في مدينة قاع الهامور تحت البحر مع صديقه بسيط نجم وشفيق حبار ومستر سلطع."
                else """🧽 **سبونج بوب سكوير بانتس (SpongeBob SquarePants)**

ابتكره عالم الأحياء البحرية والرسام **ستيفن هيلنبرغ** عام **1999**:
• تدور أحداثه في مدينة **«قاع الهامور» (Bikini Bottom)** حول سبونج بوب الطاهي المتفائل في مطعم **مقرمشات سلطع**، مع صديقه الوفي **بسيط نجم (Patrick)**، وجاره العابس **شفيق حبار**، و**ساندي أمور**."""
            }
            norm.contains("الفار الطباخ") || norm.contains("راتاتوي") || norm.contains("ratatouille") -> {
                """👨‍🍳 **فيلم الفأر الطباخ (Ratatouille - بيكسار 2007)**

من أروع أفلام استوديو **Pixar** الحائزة على جائزة الأوسكار:
• يحكي قصة الفأر الموهوب **«ريمي»** الذي يمتلك حاسة تذوق وشم استثنائية ويحلم بأن يصبح طاهياً في باريس على خطى قدوته الشيف غوستو صاحب مقولة **«الجميع يستطيع الطبخ»**، فيتعاون مع الشاب الطيب **لينغويني** لإبهار أشهر نقاد الطعام في فرنسا."""
            }
            norm.contains("كونان") || norm.contains("conan") -> {
                """🕵️‍♂️ **المحقق كونان (Detective Conan)**

سلسلة أنمي ومانغا بوليسية شهيرة للمؤلف **غوشو أوياما** بدأت عام 1994/1996:
• تدور حول المحقق العبقري الشاب **سينشي كودو** الذي يتقلص جسده إلى طفل صغير بعد تجرعه عقاراً غامضاً من **المنظمة السوداء**، فيتخذ اسم **«كونان إيدوغاوا»** ليحل أصعب القضايا الجنائية بذكاء مذهل بينما يبحث عن الحقيقة."""
            }
            else -> {
                if (isVoiceCall) {
                    "من أجمل أفلام الكرتون التي أنصحك بها: الأسد الملك، حكاية لعبة توي ستوري، الفأر الطباخ، شركة المرعبين المحدودة، وفيلم أب!"
                } else {
                    """🎬 **أجمل وأقوى أفلام الكرتون والأنيميشن العالمية (مع الدبلجة العربية الممتعة):**

1. **🦁 الأسد الملك (The Lion King):** تحفة ديزني الخالدة عن الشجاعة والوفاء، مع تيمون وبومبا بأجمل دبلجة مصرية.
2. **👹 شركة المرعبين المحدودة (Monsters, Inc.):** كوميديا وصداقة مؤثرة جداً بين «شلبي سوليفان» و«مارد وشوشني» والطفلة «بو».
3. **👨‍🍳 الفأر الطباخ (Ratatouille):** قصة ملهمة وممتعة في شوارع ومطابخ باريس عن الشغف وتحقيق الحلم.
4. **🎈 فيلم فوق (Up):** مغامرة إنسانية دافئة ومؤثرة للجد كارل والطفل الكشاف راسل بالبيت الطائر بالبالونات.
5. **🧠 قلباً وقالباً (Inside Out 1 & 2):** فكرة عبقرية تصور المشاعر داخل عقل الإنسان بطريقة كوميدية وعميقة.
6. **🤠 حكاية لعبة (Toy Story):** رحلة الصداقة الكلاسيكية بين المأمور وودي وباز يطير.

قل لي اسم أي فيلم كرتون أو شخصية تحبها وسأحكي لك قصتها وأسرارها بالتفصيل!"""
                }
            }
        }
    }

    private fun buildTechnicalKnowledgeAnswer(
        originalPrompt: String,
        norm: String,
        isArabic: Boolean,
        isVoiceCall: Boolean
    ): String {
        if (norm.contains("بلندر") || norm.contains("بليندر") || norm.contains("blender")) {
            return if (isArabic) {
                if (isVoiceCall) {
                    "في برنامج بلندر، استخدم Tab للتنقل بين Object Mode وEdit Mode، وحرف G للتحريك، وR للتدوير، وS للتحجيم، وE لعمل Extrude، ثم صَدّر النموذج بصيغة GLB لاستخدامه في التطبيقات."
                } else {
                    """🎨 **دليل سريع واحترافي لبرنامج Blender 3D:**

• **أهم الاختصارات الأساسية:**
  - `Tab`: التبديل بين **Object Mode** و**Edit Mode**.
  - `G` للتحريك (Move) | `R` للتدوير (Rotate) | `S` لتغيير الحجم (Scale) — ويمكنك الضغط بعدها على `X` أو `Y` أو `Z` لتقييد الحركة على محور واحد.
  - `E`: عمل **Extrude** لبثق الأوجه | `Ctrl + R`: إضافة **Loop Cut** | `I`: إدراج وجه داخلي **Inset**.
  - `Shift + A`: إضافة مجسم جديد أو إضاءة أو Armature للتحريك.
• **تجهيز وتصدير ملف `.GLB` نظيف لتطبيقات الموبايل:**
  1. طبّق التحويلات بـ `Ctrl + A` ← **All Transforms**.
  2. استخدم خامات **Principled BSDF** وتأكد من دمج الصور.
  3. من قائمة **File → Export → glTF 2.0 (.glb/.gltf)** اختر صيغة **glTF Binary (.glb)** مع تفعيل **Apply Modifiers**."""
                }
            } else {
                """🎨 **Blender 3D Quick Workflow & Shortcuts:**
• `Tab`: Toggle Object / Edit Mode | `G` Grab, `R` Rotate, `S` Scale (+ `X/Y/Z` axis lock)
• `E`: Extrude | `Ctrl + R`: Loop Cut | `Ctrl + A`: Apply All Transforms
• **Exporting `.glb`:** Use **File → Export → glTF 2.0 (.glb)** with *Principled BSDF* materials and *Apply Modifiers* checked."""
            }
        }

        if (norm.contains("بايثون") || norm.contains("python")) {
            return if (isArabic) {
                """🐍 **مثال برمجي نظيف بلغة Python:**

```python
def analyze_numbers(numbers: list[int]) -> dict:
    if not numbers:
        return {"count": 0, "sum": 0, "average": 0.0}
    total = sum(numbers)
    return {
        "count": len(numbers),
        "sum": total,
        "average": round(total / len(numbers), 2),
        "max": max(numbers),
    }

print(analyze_numbers([10, 25, 40, 65]))
```
إذا أردت دالة معينة أو سكريبت مخصص لفكرة محددة في Python، اكتب لي المطلوب وسأكتبه لك مباشرة!"""
            } else {
                """🐍 **Clean Python Example:**
```python
def summarize(values: list[float]) -> dict:
    return {"count": len(values), "sum": sum(values), "avg": sum(values) / max(1, len(values))}
```"""
            }
        }

        return if (isArabic) {
            if (isVoiceCall) {
                "في البرمجة والتكنولوجيا، ابدأ بتقسيم المشكلة إلى دوال صغيرة واضحة واختبر كل جزء على حدة. قل لي اللغة أو الأداة المطلوبة مثل Kotlin أو Python أو Blender وسأشرحها فوراً."
            } else {
                """💻 **إجابة تقنية وبرمجية مباشرة حول: «$originalPrompt»**

إليك مثال عملي سريع بلغة **Kotlin (Jetpack Compose)** يوضح البنية الحديثة النظيفة:

```kotlin
@Composable
fun GreetingCard(title: String, onActionClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onActionClick) {
            Text("تنفيذ الأمر")
        }
    }
}
```

💡 للحصول على توليد برمجي مخصص لأي خوارزمية معقدة عبر **Gemini 3**، يمكنك أيضاً إضافة مفتاح Google AI Studio المجاني من **القائمة الجانبية (☰) ← مفتاح API الشخصي**."""
            }
        } else {
            """💻 **Technical & Coding Overview for: "$originalPrompt"**

Here is a clean modern Kotlin Compose pattern:
```kotlin
@Composable
fun ActionCard(title: String, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = title)
        Button(onClick = onClick) { Text("Run") }
    }
}
```"""
        }
    }
}
