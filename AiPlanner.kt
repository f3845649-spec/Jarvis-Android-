package com.example.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * (Optional) AI mode: user ka koi bhi sentence Claude API ko bhejkar steps ki JSON list wapas leta hai.
 * Sirf tab chalta hai jab aapne settings mein API key daali ho aur AI mode on ho.
 * Dhyan dein: aapka type kiya command Anthropic ke server par jata hai.
 */
object AiPlanner {

    val ALLOWED = setOf(
        "open_app", "youtube_search", "google_search", "open_url", "flashlight", "volume",
        "settings_panel", "alarm", "timer", "dial", "system_key", "click_text", "type_text", "wait"
    )

    private const val SYSTEM = """You are the planner inside "Jarvis", an Android phone-control app.
The user types an instruction in Hindi, Urdu, Hinglish or English. Turn it into an ordered plan of steps the app can run.
Reply with ONLY one JSON object and nothing else (no markdown fences):
{"steps":[{"type":"...", ...}],"say":"one short sentence in the user's language"}

Step types and their fields:
- open_app: {"app":"name as shown on the home screen, e.g. YouTube, WhatsApp, Camera"}
- youtube_search: {"query":"...","play_first":true or false}  (play_first=true when the user wants a video to start playing)
- google_search: {"query":"..."}  (facts, today's date, time in a country, weather, news, prices)
- open_url: {"url":"https://..."}
- flashlight: {"on":true or false}
- volume: {"mode":"up|down|mute|max"}
- settings_panel: {"target":"wifi|bluetooth|airplane|display|sound|volume|settings"}
- alarm: {"hour":0-23,"minute":0-59,"label":"..."}
- timer: {"seconds":N}
- dial: {"number":"digits only"}
- system_key: {"key":"home|back|recents|notifications|screenshot|lock"}
- click_text: {"text":"visible label of a button or item on the screen that is currently open"}
- type_text: {"text":"text to type into the focused input field"}
- wait: {"seconds":N}  (opening an app already waits about 2.5 seconds; add wait only if an app needs longer)

Rules:
- Use only these step types. Never invent new ones.
- For several actions in one instruction, output several steps in order.
- Write search queries the way a person would type them into Google or YouTube.
- If the instruction cannot be done with these steps, return {"steps":[],"say":"short explanation"}.
- Do not add steps the user did not ask for."""

    fun plan(apiKey: String, model: String, command: String): Result<Plan> = runCatching {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm zzz", Locale.US).format(Date())

        val text = ask(apiKey, model, SYSTEM, "Phone local time: $now\nInstruction: $command", 700)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) error("AI ka jawab samajh nahi aaya")

        val obj = JSONObject(text.substring(start, end + 1))
        val arr = obj.optJSONArray("steps") ?: JSONArray()
        val steps = mutableListOf<Step>()
        for (i in 0 until arr.length()) {
            val st = Step.fromJson(arr.getJSONObject(i))
            if (st.type in ALLOWED) steps.add(st)
        }
        Plan(steps, obj.optString("say", ""))
    }

    /** Claude API ko ek sawal bhejta hai aur jawab ka text wapas deta hai (background thread se call karein). */
    fun ask(apiKey: String, model: String, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", user))
            )

        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.setRequestProperty("content-type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) error("API $code: ${resp.take(200)}")
        return JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text")
    }
}
