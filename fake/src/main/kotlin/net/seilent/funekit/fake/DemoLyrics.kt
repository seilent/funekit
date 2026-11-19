package net.seilent.funekit.fake

internal object DemoLyrics {
    private const val SECOND = 1_000L
    private const val MINUTE = 60_000L
    private const val CENTI = 10L
    private const val RTL_STEP_MS = 3_000L
    private const val RTL_END_STEP_MS = 500L
    private const val BEFORE_FROM_MS = 60_000L
    private const val BEFORE_STEP_MS = 4_000L
    private const val COPIES = 3
    private const val BEFORE_LINES = 5

    private fun stamp(ms: Long): String {
        val m = ms / MINUTE
        val s = ms % MINUTE / SECOND
        val c = ms % SECOND / CENTI
        return "[${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${c.toString().padStart(2, '0')}]"
    }

    private fun timed(lines: List<String>, at: (Int) -> Long): String = lines.mapIndexed { i, l -> stamp(at(i)) + l }.joinToString("\n")

    private fun seconds(vararg s: Int): (Int) -> Long = { s[it] * SECOND }

    private val EARLY = intArrayOf(0, 2, 4, 9, 12, 15, 18, 21, 24)

    private val SHORT = listOf(
        "Rain on the window",
        "Every street goes quiet",
        "Wait for the first train",
        "Under a lantern sky",
        "We were walking and the town was asleep",
        "Doors close",
        "Call me back again",
        "Across the empty square",
        "Nothing left to say",
    )

    private val LONG = listOf(
        "Rain on the window",
        "Every street goes quiet",
        "We walked along the river path until the harbor lights came on now",
        "Under a lantern sky",
        "Doors close",
        "Call me back again",
    )

    private val CJK = listOf(
        "雨の音",
        "窓の外",
        "長い夜が明けるまで、ずっと君のことを考えていた",
        "灯りが消える",
        "朝の光",
        "またね",
    )

    private val ACCENT = listOf(
        "Café crème à l'aube",
        "Señor del río",
        "Über die Brücke",
        "Ça ira, ça ira",
        "Søren og Åse",
    )

    private val PLAIN = listOf(
        listOf("Morning light on the stairs", "Coffee going cold", "Radio low in the kitchen", "Nobody up but me", "Pages turning slowly"),
        listOf("Out along the harbor", "Gulls above the cranes", "Salt on every railing", "Boats that never leave"),
        listOf("Noon and the market", "Voices on the corner", "Bread and oranges", "Change in my pocket", "A bus that is late"),
        listOf("Afternoon rain", "Umbrellas in a row", "Puddles on the platform", "Steam on the glass"),
        listOf("Evening on the bridge", "Lamps come on one by one", "The river keeps on moving", "Somebody laughing", "A door left open"),
        listOf("Night and the last train", "Ticket in my hand", "Home is the next stop", "Sleep comes easy", "Lights out"),
    )

    private val HEBREW = listOf(
        "גשם קל בחלון",
        "הרחוב שותק",
        "אני הולך לאט",
        "האור בקצה",
        "שיר ישן ברדיו",
        "הלילה ארוך",
        "צעדים על אבן",
        "רוח מהים",
        "בוקר חדש",
    )

    private val ARABIC = listOf(
        "مطر خفيف على النافذة",
        "الشارع صامت",
        "أمشي ببطء",
        "الضوء في النهاية",
        "أغنية قديمة في المذياع",
        "الليل طويل",
        "خطوات على الحجر",
        "ريح من البحر",
        "صباح جديد",
    )

    private val POINTED = listOf(
        "שָׁלוֹם עַל הַבַּיִת",
        "גֶּשֶׁם בַּלַּיְלָה",
        "אוֹר רִאשׁוֹן",
        "יָם שָׁקֵט",
        "שִׁיר לַבֹּקֶר",
    )

    private val MIXED = listOf(
        "الطريق 66 إلى البحر",
        "radio على الموجة 101",
        "نحن في 2024 now",
        "Track 3 من الألبوم",
        "صوت DJ في الليل",
        "12 ساعة of rain",
    )

    private val ARABIC_LONG = listOf(
        "هذا سطر طويل جدا يستمر بعد حافة الشاشة لنرى كيف يلتف النص عندما لا يتسع في سطر واحد",
        "كلمةطويلةجدابدونمسافاتعلىالإطلاقحتىالنهاية",
        "قصير",
        "نهاية",
    )

    private val HARAKAT = listOf(
        "سَلامٌ عَلَى الْبَحْرِ",
        "اللَّيْلُ طَوِيلٌ",
        "نُورُ الصَّبَاحِ",
        "كَلِمَةٌ أَخِيرَةٌ",
    )

    private val SORANI = listOf(
        "ئێوارەیەکی ئارام",
        "باران لە پەنجەرە",
        "ڕێگای ماڵەوە",
    )

    private fun plainCopies(lines: List<String>) = List(COPIES) { lines.joinToString("\n") }.joinToString("\n\n")

    val DEMO = "[ti:demo]\n" + timed(listOf("Rain on the window", "Every street goes quiet", "A lyric line that runs on past the right edge of the row"), seconds(0, 5, 10))

    val SHAPES: Map<String, String> = linkedMapOf(
        "short" to "[ti:demo]\n" + timed(SHORT) { EARLY[it] * SECOND },
        "long" to timed(LONG) { EARLY[it] * SECOND },
        "cjk" to timed(CJK) { EARLY[it] * SECOND },
        "accent" to timed(ACCENT) { EARLY[it] * SECOND },
        "before" to timed(SHORT.take(BEFORE_LINES)) { BEFORE_FROM_MS + it * BEFORE_STEP_MS },
        "idtag" to "[ar:demo]\n[ti:demo]\n[al:demo]\n[by:demo]\n[offset:0]\n[length:03:00]",
        "plain" to "[ar:demo]\n[ti:demo]\n" + PLAIN.joinToString("\n\n") { it.joinToString("\n") },
        "he" to timed(HEBREW) { it * RTL_STEP_MS },
        "hepoint" to timed(POINTED) { it * RTL_STEP_MS },
        "heplain" to plainCopies(HEBREW),
        "ar" to timed(ARABIC) { it * RTL_STEP_MS },
        "arend" to timed(ARABIC) { it * RTL_END_STEP_MS },
        "arplain" to plainCopies(ARABIC),
        "armix" to timed(MIXED) { it * RTL_STEP_MS },
        "arlong" to timed(ARABIC_LONG) { it * RTL_STEP_MS },
        "harakat" to timed(HARAKAT) { it * RTL_STEP_MS },
        "sorani" to timed(SORANI) { it * RTL_STEP_MS },
    )
}
