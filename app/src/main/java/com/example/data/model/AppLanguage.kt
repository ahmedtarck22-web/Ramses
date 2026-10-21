package com.example.data.model

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    ARABIC("ar", "العربية", "🇪🇬"),
    ENGLISH("en", "English", "🇬🇧")
}

object AppStrings {
    fun appName(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "اسأل رمسيس"
        AppLanguage.ENGLISH -> "Ask Rameses"
    }

    fun appSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "الذكاء الاصطناعي الفرعوني الملكي"
        AppLanguage.ENGLISH -> "Royal Pharaonic AI Intelligence"
    }

    fun inputPlaceholder(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "...اسأل رمسيس عن أي شيء"
        AppLanguage.ENGLISH -> "Ask Rameses anything..."
    }

    fun personalKeyActive(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "مفتاحك الشخصي نشط • 1.5M توكن"
        AppLanguage.ENGLISH -> "Personal Key Active • 1.5M Tokens"
    }

    fun freeTokensNotice(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "لكل مستخدم 1.5 مليون توكن مجاناً"
        AppLanguage.ENGLISH -> "1.5 Million Free Tokens per User"
    }

    fun liveCallTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "مكالمة رمسيس المباشرة"
        AppLanguage.ENGLISH -> "Rameses Live Call"
    }

    fun liveStatusListening(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "يستمع إليك الآن... تفضل بالحديث"
        AppLanguage.ENGLISH -> "Listening to you now... Speak freely"
    }

    fun liveStatusThinking(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "رمسيس يستحضر الحكمة السريعة..."
        AppLanguage.ENGLISH -> "Rameses is reflecting..."
    }

    fun liveStatusSpeaking(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "رمسيس يتحدث إليك مباشرة..."
        AppLanguage.ENGLISH -> "Rameses is speaking to you..."
    }

    fun liveStatusIdle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "انقر على الميكروفون للتحدث مع الملك"
        AppLanguage.ENGLISH -> "Tap the microphone to speak with the King"
    }

    fun liveCallHint(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "تحدث معي بصوتك بحرية في التاريخ أو العلوم أو يومك، وسأجيبك فوراً."
        AppLanguage.ENGLISH -> "Speak with me about history, science, or your daily life. I answer instantly."
    }

    fun searchGroundingEnabled(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "البحث الحي عبر Google نشط"
        AppLanguage.ENGLISH -> "Live Google Search Active"
    }

    fun searchGroundingDisabled(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "البحث الحي متوقف (استجابة فائقة السرعة)"
        AppLanguage.ENGLISH -> "Live Search Off (Ultra-fast response)"
    }

    fun thinkingMessage(lang: AppLanguage, withSearch: Boolean): String = when (lang) {
        AppLanguage.ARABIC -> if (withSearch) "رمسيس يبحث ويوثق الحكمة..." else "رمسيس يفكر..."
        AppLanguage.ENGLISH -> if (withSearch) "Rameses is searching the web..." else "Rameses is thinking..."
    }

    fun retryButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "إعادة المحاولة"
        AppLanguage.ENGLISH -> "Retry"
    }

    fun changeApiKey(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "تغيير المفتاح"
        AppLanguage.ENGLISH -> "Change API Key"
    }

    fun openAiStudio(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "فتح AI Studio"
        AppLanguage.ENGLISH -> "Open AI Studio"
    }

    fun copyText(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "نسخ"
        AppLanguage.ENGLISH -> "Copy"
    }

    fun speakText(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "استماع"
        AppLanguage.ENGLISH -> "Listen"
    }

    fun newSession(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "محادثة جديدة"
        AppLanguage.ENGLISH -> "New Chat"
    }

    fun drawerKnowledge(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "قاعدة المعرفة والوثائق (RAG)"
        AppLanguage.ENGLISH -> "Knowledge Base (RAG)"
    }

    fun drawerKey(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "مفتاح الذكاء الاصطناعي الخاص"
        AppLanguage.ENGLISH -> "Personal AI API Key"
    }

    fun drawerAccount(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "الحساب والملف الملكي"
        AppLanguage.ENGLISH -> "Account & Royal Profile"
    }

    fun drawerVoiceCall(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "مكالمة رمسيس الصوتية التفاعلية"
        AppLanguage.ENGLISH -> "Interactive Voice Call (Ramses Voice)"
    }

    fun clearAllChats(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "مسح كافة المحادثات"
        AppLanguage.ENGLISH -> "Clear All Conversations"
    }

    fun endCall(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "إنهاء المكالمة"
        AppLanguage.ENGLISH -> "End Call"
    }

    fun muteSpeaker(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "كتم الصوت"
        AppLanguage.ENGLISH -> "Mute Audio"
    }

    fun switchLanguage(lang: AppLanguage): String = when (lang) {
        AppLanguage.ARABIC -> "English"
        AppLanguage.ENGLISH -> "العربية"
    }
}
