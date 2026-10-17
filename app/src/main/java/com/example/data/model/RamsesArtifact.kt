package com.example.data.model

data class RamsesArtifact(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val dynastyAr: String,
    val dynastyEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val factAr: String,
    val factEn: String,
    val modelPath: String,
    val iconEmoji: String
) {
    fun title(language: AppLanguage): String = if (language == AppLanguage.ARABIC) titleAr else titleEn
    fun dynasty(language: AppLanguage): String = if (language == AppLanguage.ARABIC) dynastyAr else dynastyEn
    fun description(language: AppLanguage): String = if (language == AppLanguage.ARABIC) descriptionAr else descriptionEn
    fun fact(language: AppLanguage): String = if (language == AppLanguage.ARABIC) factAr else factEn

    companion object {
        val ALL_ARTIFACTS = listOf(
            RamsesArtifact(
                id = "ramses_statue",
                titleAr = "تمثال رمسيس الثاني العظيم",
                titleEn = "Colossus of Ramses II",
                dynastyAr = "الأسرة التاسعة عشرة (1279–1213 ق.م)",
                dynastyEn = "19th Dynasty (1279–1213 BCE)",
                descriptionAr = "تمثال ملكي مهيب للملك رمسيس الثاني يرتدي غطاء النمس واللحية الفرعونية، حاملاً صولجان العظمة والإمبراطورية المصرية في أوج مجدها.",
                descriptionEn = "Majestic royal colossus of King Ramses II wearing the striped Nemes headdress and royal beard, personifying the peak of Egyptian imperial might.",
                factAr = "حكم رمسيس الثاني مصر لمدة 66 عاماً، وخاض معركة قادش التاريخية، وشيّد معبدي أبو سمبل وصرح الرامسيوم الخالد.",
                factEn = "Ramses II reigned for 66 years, fought the Battle of Kadesh, and erected the immortal temples of Abu Simbel and the Ramesseum.",
                modelPath = "models/ramses_statue.glb",
                iconEmoji = "👑"
            ),
            RamsesArtifact(
                id = "great_pyramid",
                titleAr = "هرم خوفو الأكبر بالجيزة",
                titleEn = "The Great Pyramid of Giza",
                dynastyAr = "الأسرة الرابعة (حوالي 2560 ق.م)",
                dynastyEn = "4th Dynasty (c. 2560 BCE)",
                descriptionAr = "أعجوبة العالم القديم الباقية، تتوج قمتها بهرم صغير مطلي بالذهب الخالص (حجر بن بن) يعكس أشعة الإله رع عند الفجر.",
                descriptionEn = "The only surviving ancient world wonder, crowned with a gilded Benben capstone reflecting the dawn rays of the sun god Ra.",
                factAr = "شُيّد الهرم بدقة فلكية متناهية تواجه الجهات الأصلية الأربع بنسبة خطأ تقارب صفراً، ويضم نحو 2.3 مليون كتلة حجرية ضخمة.",
                factEn = "Engineered with astronomical alignment to the cardinal points and assembled from 2.3 million monumental limestone and granite blocks.",
                modelPath = "models/great_pyramid.glb",
                iconEmoji = "🔺"
            ),
            RamsesArtifact(
                id = "golden_mask",
                titleAr = "القناع الجنائزي الذهبي الملكي",
                titleEn = "Royal Golden Death Mask",
                dynastyAr = "الأسرة الثامنة عشرة (الدولة الحديثة)",
                dynastyEn = "18th Dynasty (New Kingdom)",
                descriptionAr = "تحفة الصياغة الفرعونية من الذهب الخالص عيار 22، مرصع بأحجار اللازورد النيلي والعقيق والفيروز، لحماية الروح الملكية إلى الأبد.",
                descriptionEn = "The pinnacle of ancient goldsmithing crafted from solid 22-karat gold inlaid with Nilotic lapis lazuli, turquoise, and carnelian.",
                factAr = "يزن القناع الملكي نحو 10.2 كيلوجرامات من الذهب الصافي، ونُقشت على ظهره تعويذة سحرية من كتاب الموتى لحماية حواس الملك.",
                factEn = "Weighing over 10.2 kilograms of refined gold, engraved with protective incantations from Chapter 151b of the Book of the Dead.",
                modelPath = "models/golden_mask.glb",
                iconEmoji = "✨"
            ),
            RamsesArtifact(
                id = "sacred_scarab",
                titleAr = "تميمة الجعران المقدس المجنح (خبري)",
                titleEn = "Winged Sacred Scarab (Khepri)",
                dynastyAr = "عصر الدولة الحديثة",
                dynastyEn = "New Kingdom Era",
                descriptionAr = "رمز التجدد والولادة المستمرة وشروق الشمس، يدفع قرص الشمس الذهبي المشع بأجنحة ملكية من اللازورد والفيروز الأخضر.",
                descriptionEn = "Sacred emblem of continuous rebirth and transformation, propelling the radiant solar disc with wings of cobalt lapis and emerald turquoise.",
                factAr = "كان يوضع جعران القلب على صدر المتوفى أثناء التحنيط ليحمي قلبه ويمنعه من الشهادة ضده في محكمة أوزوريس الخالدة.",
                factEn = "Heart scarabs were consecrated during mummification to safeguard the heart during its weighing against the feather of Ma'at.",
                modelPath = "models/sacred_scarab.glb",
                iconEmoji = "🪲"
            ),
            RamsesArtifact(
                id = "eye_of_horus",
                titleAr = "تميمة عين حورس الملكية (أوجات)",
                titleEn = "Eye of Horus Talisman (Wedjat)",
                dynastyAr = "رمز الحماية والشفاء الخالد",
                dynastyEn = "Eternal Symbol of Protection & Healing",
                descriptionAr = "عين الصقر الإلهي المكتملة، صِيغت بإطار ذهبي ساطع وبؤبؤ من اللازورد العميق، تميمة الحماية والشفاء والنصر على قوى الفوضى.",
                descriptionEn = "The restored divine falcon eye framed in radiant gold and deep lapis, representing wholeness, royal healing, and spiritual invulnerability.",
                factAr = "استخدم حكماء مصر القديمة أجزاء عين حورس في العمليات الحسابية الدقيقة لتمثيل الكسور الستة لقياس المقادير الطبية ومكاييل الغلال.",
                factEn = "Ancient Egyptian physicians and scribes used the anatomical fractions of the Wedjat eye to measure medicinal recipes and sacred grain.",
                modelPath = "models/eye_of_horus.glb",
                iconEmoji = "👁️"
            )
        )
    }
}
