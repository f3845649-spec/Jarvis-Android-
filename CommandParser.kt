package com.example.jarvis

/**
 * Rule-based parser jo bina internet/AI ke chalta hai (Hindi, Urdu, Hinglish, English).
 * Aam sentences pakadta hai; mushkil ya alag dhang ke sentences ke liye AI mode (AiPlanner) behtar hai.
 */
object CommandParser {

    private class Tok(val raw: String, val key: String)

    private fun s(vararg w: String): Set<String> =
        w.map { Text.norm(it) }.filter { it.isNotEmpty() }.toSet()

    // ---------- Word lists (Hindi / Urdu / Roman spellings) ----------
    private val YT = s("youtube", "utube", "yutub", "यूट्यूब", "यूटयूब", "यूट्यब", "युट्यूब", "यूट्युब", "یوٹیوب")
    private val GOOGLE = s("google", "googel", "gugal", "गूगल", "गुगल", "گوگل")

    private val SEARCH = s(
        "search", "serch", "sarch", "सर्च", "सेर्च", "खोज", "खोजो", "खोजें", "खोजिए", "खोजिये", "तलाश",
        "ढूंढो", "ढूंढ", "ढूंढिए", "dhoondo", "dhundo", "dhoond", "khojo", "khoj", "سرچ", "تلاش", "ڈھونڈو", "find", "lookup"
    )
    private val PLAY = s(
        "play", "प्ले", "चला", "चलाओ", "चलादो", "चलाइए", "चलाइये", "chala", "chalao", "chalado",
        "laga", "lagao", "lagado", "लगा", "लगाओ", "लगादो", "लगाइए", "لگا", "لگاؤ", "چلا", "چلاؤ", "لگادو", "چلادو"
    )
    private val OPEN = s(
        "open", "ओपन", "खोलो", "खोल", "खोलें", "खोलिए", "खोलिये", "kholo", "khol", "kholiye",
        "کھولو", "کھول", "کھولیں", "launch", "लॉन्च", "लांच", "start", "स्टार्ट"
    )
    private val DO = s(
        "करो", "कर", "करें", "कीजिए", "कीजिये", "करना", "दो", "दीजिए", "दीजिये", "दे", "देना",
        "karo", "kar", "kardo", "kijiye", "do", "de", "dena", "please", "plz", "pls", "कृपया", "प्लीज",
        "کرو", "کر", "کریں", "دو", "دیں", "دے", "मुझे", "मेरे", "मेरा", "मेरी", "मैं", "मैंने", "mujhe", "mere",
        "ज़रा", "zara", "बताओ", "बताइए", "बताइये", "बता", "batao", "bataiye", "bata", "بتاؤ", "بتا", "بتائیں",
        "दिखाओ", "दिखा", "dikhao", "show", "دکھاؤ", "देखता", "देखती", "dekhta", "dekhti", "हूं", "हूँ", "hun", "hoon",
        "ہوں", "دیکھتا", "دیکھتی", "और", "aur", "and", "तो", "फिर"
    )
    private val PREP = s(
        "पर", "में", "मे", "मै", "से", "को", "के", "का", "की", "लिए", "liye", "par", "pe", "mein", "mai",
        "ko", "ke", "ka", "ki", "on", "in", "for", "پر", "میں", "سے", "کو", "کے", "کا", "کی", "لیے"
    )
    private val FILLER = s(
        "जाकर", "जा", "जाओ", "जाइए", "जाइये", "jakar", "ja", "jao", "جا", "جاؤ", "جاکر",
        "वाला", "वाली", "वाले", "wala", "wali", "वो", "वह", "woh", "vo", "ye", "यह", "ये", "वही",
        "topic", "टॉपिक", "टोपिक", "ٹاپک", "कि", "वीडियो", "वीडियोज", "video", "videos", "ویڈیو", "ویڈیوز",
        "app", "ऐप", "एप", "एप्लिकेशन", "application", "ایپ", "ایپلیکیشن"
    )
    private val EDGE: Set<String> = DO + PREP + SEARCH + PLAY + OPEN + FILLER

    private val TORCH = s("torch", "tourch", "टॉर्च", "टार्च", "flashlight", "फ्लैशलाइट", "फ्लैश", "flash", "ٹارچ")
    private val ON = s("on", "ऑन", "चालू", "जलाओ", "जला", "जलाइए", "jalao", "jala", "chalu", "آن", "چالو", "جلاؤ", "جلا", "चला", "चलाओ", "chala", "chalao")
    private val OFF = s("off", "ऑफ", "बंद", "बुझाओ", "बुझा", "band", "bujhao", "bujha", "آف", "بند", "بجھاؤ", "بجھا", "stop", "रोको")

    private val VOLUME = s("volume", "वॉल्यूम", "वॉल्युम", "वोल्यूम", "आवाज", "awaz", "awaaz", "آواز")
    private val UP = s("up", "बढ़ा", "बढ़ाओ", "बढाओ", "ज्यादा", "ज़्यादा", "जादा", "badha", "badhao", "zyada", "high", "increase", "तेज", "tez", "تیز", "بڑھاؤ", "بڑھا", "زیادہ")
    private val DOWN = s("down", "कम", "घटा", "घटाओ", "kam", "ghata", "ghatao", "low", "decrease", "धीमा", "dheema", "آہستہ", "کم", "گھٹاؤ")
    private val MUTE = s("mute", "म्यूट", "silent", "साइलेंट", "चुप", "khamosh", "خاموش", "میوٹ")
    private val MAXW = s("max", "full", "फुल", "मैक्स", "पूरा")

    private val ALARM = s("alarm", "अलार्म", "अलारम", "الارم")
    private val TIMER = s("timer", "टाइमर", "ٹائمر")
    private val PM = s("pm", "शाम", "रात", "दोपहर", "sham", "raat", "dopahar", "dophar", "شام", "رات", "دوپہر", "evening", "night", "afternoon")
    private val AM = s("am", "सुबह", "सवेरे", "subah", "savere", "صبح", "morning")
    private val NIGHT = s("रात", "raat", "night", "رات")
    private val SADHE = s("साढ़े", "साढे", "sadhe", "ساڑھے")
    private val SAWA = s("सवा", "sawa", "سوا")
    private val PAUNE = s("पौने", "paune", "پونے")
    private val UNIT_MIN = s("minute", "minutes", "min", "mins", "मिनट", "मिनिट", "minat", "منٹ", "minit")
    private val UNIT_SEC = s("second", "seconds", "sec", "secs", "सेकंड", "सेकेंड", "सेकण्ड", "سیکنڈ")
    private val UNIT_HOUR = s("hour", "hours", "hr", "घंटा", "घंटे", "घण्टा", "ghanta", "ghante", "گھنٹہ", "گھنٹے")

    private val WIFI = s("wifi", "वाईफाई", "वायफाय", "वाइफाइ", "वाई", "wi", "وائی", "internet", "इंटरनेट", "انٹرنیٹ")
    private val BT = s("bluetooth", "ब्लूटूथ", "ब्लूटुथ", "بلوٹوتھ")
    private val AIRPLANE = s("airplane", "aeroplane", "एयरप्लेन")
    private val BRIGHT = s("brightness", "ब्राइटनेस", "روشنی", "चमक")
    private val CALL = s("call", "कॉल", "डायल", "dial", "کال", "ڈائل", "फोन", "phone", "فون")

    private val HOME = s("home", "होम", "ہوم")
    private val BACK = s("back", "पीछे", "वापस", "बैक", "واپس", "پیچھے")
    private val RECENTS = s("recent", "recents", "रीसेंट", "रिसेंट", "ریسنٹ")
    private val NOTIF = s("notification", "notifications", "नोटिफिकेशन", "नोटिफिकेशंस", "نوٹیفکیشن")
    private val SHOT = s("screenshot", "स्क्रीनशॉट")
    private val LOCK = s("lock", "लॉक", "لاک")

    private val LOOKUP = s(
        "क्या", "कौन", "कब", "कहां", "कहाँ", "कैसे", "कितना", "कितने", "कितनी", "क्यों",
        "kya", "kaun", "kab", "kahan", "kaise", "kitna", "kyun", "kyon",
        "what", "who", "when", "where", "how", "why", "which",
        "کیا", "کون", "کب", "کہاں", "کیسے", "کتنا", "کیوں",
        "date", "डेट", "तारीख", "time", "टाइम", "समय", "وقت", "ٹائم",
        "weather", "मौसम", "موسم", "news", "खबर", "خبر", "price", "भाव", "score", "स्कोर"
    )

    private val SPLIT = Regex(
        "\\s+(?:और फिर|उसके बाद|फिर|then|phir|fir|after that|aur phir|پھر)\\s+",
        RegexOption.IGNORE_CASE
    )

    // ---------- Main function ----------
    fun parse(text: String): List<Step> {
        val steps = mutableListOf<Step>()
        for (part in SPLIT.split(text.trim())) {
            if (part.isNotBlank()) steps.addAll(parsePart(part.trim()))
        }
        return steps
    }

    private fun tokenize(s: String): List<Tok> =
        s.trim().split(Regex("\\s+"))
            .map { Tok(Text.trimPunct(it), Text.norm(it)) }
            .filter { it.key.isNotEmpty() }

    private fun trimEdges(l: List<Tok>, stop: Set<String>): List<Tok> {
        var a = 0
        var b = l.size
        while (a < b && l[a].key in stop) a++
        while (b > a && l[b - 1].key in stop) b--
        return l.subList(a, b)
    }

    private fun join(l: List<Tok>): String = l.joinToString(" ") { it.raw }

    private fun parsePart(part: String): List<Step> {
        val toks = tokenize(part)
        if (toks.isEmpty()) return emptyList()
        val keys = toks.map { it.key }
        fun has(set: Set<String>): Boolean = keys.any { it in set }
        val digits = Text.asciiDigits(part)

        val ytIdx = keys.indexOfFirst { it in YT }
        val ggIdx = keys.indexOfFirst { it in GOOGLE }

        // 1) "youtube/google par ... search karo / chala do"
        if ((ytIdx >= 0 || ggIdx >= 0) && (has(SEARCH) || has(PLAY))) {
            val isYt = ytIdx >= 0 && (ggIdx < 0 || ytIdx < ggIdx)
            val idx = if (isYt) ytIdx else ggIdx
            val rest = toks.filterIndexed { i, t -> i != idx && !(i == idx + 1 && t.key in PREP) }
            val query = join(trimEdges(rest, EDGE))
            return if (isYt) {
                if (query.isBlank()) listOf(Step("open_app", mapOf("app" to "youtube")))
                else listOf(
                    Step("youtube_search", mapOf("query" to query, "play_first" to has(PLAY).toString()))
                )
            } else {
                if (query.isBlank()) listOf(Step("open_app", mapOf("app" to "google")))
                else listOf(Step("google_search", mapOf("query" to query)))
            }
        }

        // 2) Torch
        if (has(TORCH)) {
            return listOf(Step("flashlight", mapOf("on" to (!has(OFF)).toString())))
        }

        // 3) Volume
        if (has(VOLUME)) {
            val mode = when {
                has(MUTE) -> "mute"
                has(MAXW) -> "max"
                has(DOWN) -> "down"
                has(UP) -> "up"
                else -> ""
            }
            return if (mode.isEmpty()) listOf(Step("settings_panel", mapOf("target" to "volume")))
            else listOf(Step("volume", mapOf("mode" to mode)))
        }

        // 4) Alarm / Timer
        if (has(ALARM)) return listOf(alarmStep(keys, digits))
        if (has(TIMER)) {
            val secs = durationSeconds(digits)
            return listOf(Step("timer", mapOf("seconds" to secs.toString())))
        }

        // 5) Wi-Fi / Bluetooth / Airplane / Brightness panel
        val panel = when {
            has(WIFI) -> "wifi"
            has(BT) -> "bluetooth"
            has(AIRPLANE) -> "airplane"
            has(BRIGHT) -> "display"
            else -> ""
        }
        if (panel.isNotEmpty()) return listOf(Step("settings_panel", mapOf("target" to panel)))

        // 6) Number dial
        if (has(CALL)) {
            val num = Regex("\\+?\\d[\\d\\s-]{2,}\\d").find(digits)?.value?.replace(Regex("[\\s-]"), "")
            if (num != null) return listOf(Step("dial", mapOf("number" to num)))
        }

        // 7) System buttons (chhote sentences)
        if (toks.size <= 5) {
            val key = when {
                has(HOME) -> "home"
                has(BACK) -> "back"
                has(RECENTS) -> "recents"
                has(NOTIF) -> "notifications"
                has(SHOT) -> "screenshot"
                has(LOCK) -> "lock"
                else -> ""
            }
            if (key.isNotEmpty()) return listOf(Step("system_key", mapOf("key" to key)))
        }

        // 8) App kholo
        if (has(OPEN) || has(PLAY)) {
            val name = join(trimEdges(toks, EDGE))
            if (name.isNotBlank()) return listOf(Step("open_app", mapOf("app" to name)))
        }

        // 9) Sirf "youtube" / "google"
        if (ytIdx >= 0) return listOf(Step("open_app", mapOf("app" to "youtube")))
        if (ggIdx >= 0) return listOf(Step("open_app", mapOf("app" to "google")))

        // 10) Sawal / jaankari jaisa kuch -> Google search
        if (has(LOOKUP)) {
            val q = join(trimEdges(toks, EDGE))
            if (q.isNotBlank()) return listOf(Step("google_search", mapOf("query" to q)))
        }

        return emptyList()
    }

    private fun alarmStep(keys: List<String>, text: String): Step {
        var hour = -1
        var min = 0
        val hm = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})").find(text)
        if (hm != null) {
            hour = hm.groupValues[1].toInt()
            min = hm.groupValues[2].toInt()
        } else {
            val n = Regex("\\d{1,2}").find(text)
            if (n != null) {
                hour = n.value.toInt()
                if (keys.any { it in SADHE }) {
                    min = 30
                } else if (keys.any { it in SAWA }) {
                    min = 15
                } else if (keys.any { it in PAUNE }) {
                    hour -= 1
                    min = 45
                }
            }
        }
        if (hour < 0 || hour > 23 || min > 59) return Step("alarm", emptyMap())
        if (keys.any { it in PM } && hour < 12) hour += 12
        if (keys.any { it in AM } && hour == 12) hour = 0
        if (keys.any { it in NIGHT } && hour == 12) hour = 0
        return Step("alarm", mapOf("hour" to hour.toString(), "minute" to min.toString()))
    }

    private fun durationSeconds(text: String): Int {
        var total = 0
        for (m in Regex("(\\d+)\\s*([^\\d\\s]+)").findAll(text)) {
            val n = m.groupValues[1].toIntOrNull() ?: continue
            val unit = Text.norm(m.groupValues[2])
            total += when (unit) {
                in UNIT_HOUR -> n * 3600
                in UNIT_MIN -> n * 60
                in UNIT_SEC -> n
                else -> 0
            }
        }
        return total
    }
}
