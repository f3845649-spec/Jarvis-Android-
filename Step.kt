package com.example.jarvis

import org.json.JSONObject

/** Ek kaam. type = "youtube_search", "open_app" etc.; args = uske parameters (sab String). */
data class Step(val type: String, val args: Map<String, String> = emptyMap()) {

    fun describe(): String = when (type) {
        "open_app" -> "App kholo: ${args["app"]}"
        "youtube_search" -> "YouTube search: ${args["query"]}" +
            (if (args["play_first"] == "true") " (pehli video chalao)" else "")
        "google_search" -> "Google search: ${args["query"]}"
        "open_url" -> "Link kholo: ${args["url"]}"
        "flashlight" -> if (args["on"] == "false") "Torch band" else "Torch on"
        "volume" -> "Volume: ${args["mode"]}"
        "settings_panel" -> "Setting: ${args["target"]}"
        "alarm" ->
            if (args["hour"] != null) "Alarm ${args["hour"]}:${(args["minute"] ?: "0").padStart(2, '0')}"
            else "Alarm list"
        "timer" -> "Timer ${args["seconds"]} second"
        "dial" -> "Dial: ${args["number"]}"
        "system_key" -> "System button: ${args["key"]}"
        "click_text" -> "Tap: ${args["text"]}"
        "type_text" -> "Type: ${args["text"]}"
        "wait" -> "Ruko ${args["seconds"]} second"
        else -> type
    }

    companion object {
        fun fromJson(o: JSONObject): Step {
            val args = mutableMapOf<String, String>()
            for (k in o.keys()) {
                if (k != "type") args[k] = o.get(k).toString()
            }
            return Step(o.getString("type"), args)
        }
    }
}

/** AI ya parser se mila poora plan. say = user ko dikhane wala chhota jawab. */
data class Plan(val steps: List<Step>, val say: String = "")

/** Screen par dikhne wala simple log (Activity band ho tab bhi safe). */
object JarvisLog {
    private val lines = mutableListOf<String>()

    @Volatile
    var listener: (() -> Unit)? = null

    fun add(s: String) {
        synchronized(lines) {
            lines.add(s)
            while (lines.size > 200) lines.removeAt(0)
        }
        listener?.invoke()
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }
}
