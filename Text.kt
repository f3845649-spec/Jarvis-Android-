package com.example.jarvis

import java.text.Normalizer

/** Shabd milane ke helpers (Hindi / Urdu / Roman teeno ke liye). */
object Text {

    private const val PUNCT = ".,!?;:।॥\"'“”‘’()[]{}…،؟"

    fun trimPunct(s: String): String = s.trim { it in PUNCT }

    /** lowercase + nukta/hyphen/ZWJ hatana + chandrabindu->anusvar, taaki spelling ke farq se match na toote. */
    fun norm(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in decomposed) {
            when (c) {
                '\u093C', '\u200D', '\u200C', '-', '\u2011' -> {}
                '\u0901' -> sb.append('\u0902')
                '\u064A' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                else -> sb.append(c)
            }
        }
        return trimPunct(sb.toString())
    }

    /** Hindi aur Urdu digits ko 0-9 mein badalna. */
    fun asciiDigits(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (Character.isDigit(c)) sb.append(Character.digit(c, 10)) else sb.append(c)
        }
        return sb.toString()
    }
}
