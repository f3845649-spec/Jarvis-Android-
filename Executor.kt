package com.example.jarvis

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings

/**
 * Step ki list ko ek-ek karke chalata hai.
 * ctx = JarvisService ho to background se bhi apps khol sakta hai; svc = screen par tap/type ke liye.
 */
class Executor(private val ctx: Context, private val svc: JarvisService?) {

    private val handler = Handler(Looper.getMainLooper())

    fun run(steps: List<Step>) = next(steps, 0)

    private fun next(steps: List<Step>, i: Int) {
        if (i >= steps.size) {
            JarvisLog.add("✓ Sab kaam poore")
            return
        }
        val s = steps[i]
        val wait = try {
            val w = exec(s)
            JarvisLog.add("✓ ${s.describe()}")
            w
        } catch (e: Exception) {
            JarvisLog.add("✗ ${s.describe()} → ${e.message ?: e.javaClass.simpleName}")
            500L
        }
        handler.postDelayed({ next(steps, i + 1) }, wait)
    }

    /** Ek step turant chalata hai (Agent isse use karta hai); wapas: agle step se pehle kitne ms rukna hai. */
    fun execOne(s: Step): Long = exec(s)

    /** Kaam karta hai aur batata hai ki agle step se pehle kitne ms rukna hai. */
    private fun exec(s: Step): Long {
        val a = s.args
        return when (s.type) {
            "open_app" -> if (openApp(a["app"].orEmpty())) 2500L else 500L
            "youtube_search" -> {
                val play = a["play_first"] == "true"
                youtube(a["query"].orEmpty(), play)
                if (play) 12000L else 3000L
            }
            "google_search" -> {
                google(a["query"].orEmpty())
                3000L
            }
            "open_url" -> {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse(a["url"].orEmpty())))
                3000L
            }
            "flashlight" -> {
                torch(a["on"] != "false")
                500L
            }
            "volume" -> {
                volume(a["mode"] ?: "up")
                500L
            }
            "settings_panel" -> {
                panel(a["target"] ?: "settings")
                2000L
            }
            "alarm" -> {
                alarm(a)
                1500L
            }
            "timer" -> {
                timer(a)
                1500L
            }
            "dial" -> {
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a["number"].orEmpty()))))
                2000L
            }
            "system_key" -> {
                needSvc().globalKey(a["key"].orEmpty())
                1200L
            }
            "click_text" -> {
                needSvc().clickText(a["text"].orEmpty())
                3000L
            }
            "type_text" -> {
                needSvc().typeText(a["text"].orEmpty())
                800L
            }
            "wait" -> ((a["seconds"]?.toDoubleOrNull() ?: 1.0) * 1000).toLong().coerceIn(200L, 30000L)
            else -> {
                JarvisLog.add("? Unknown step: ${s.type}")
                200L
            }
        }
    }

    private fun needSvc(): JarvisService =
        svc ?: throw IllegalStateException("Iske liye Accessibility service on karein")

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    // ---------- App kholna ----------
    private val aliases: Map<String, List<String>> = mapOf(
        "यूट्यूब" to listOf("youtube"), "यूटयूब" to listOf("youtube"), "یوٹیوب" to listOf("youtube"),
        "व्हाट्सएप" to listOf("whatsapp"), "व्हाट्सऐप" to listOf("whatsapp"), "वॉट्सएप" to listOf("whatsapp"),
        "वाट्सएप" to listOf("whatsapp"), "व्हाट्सअप" to listOf("whatsapp"), "واٹس" to listOf("whatsapp"),
        "क्रोम" to listOf("chrome"), "کروم" to listOf("chrome"),
        "कैमरा" to listOf("camera"), "कैमेरा" to listOf("camera"), "کیمرہ" to listOf("camera"),
        "सेटिंग" to listOf("settings"), "سیٹنگ" to listOf("settings"), "ترتیبات" to listOf("settings"),
        "गैलरी" to listOf("gallery", "photos"), "گیلری" to listOf("gallery", "photos"),
        "फेसबुक" to listOf("facebook"), "فیسبک" to listOf("facebook"),
        "इंस्टाग्राम" to listOf("instagram"), "انسٹاگرام" to listOf("instagram"),
        "टेलीग्राम" to listOf("telegram"), "ٹیلیگرام" to listOf("telegram"),
        "स्नैपचैट" to listOf("snapchat"), "टिकटॉक" to listOf("tiktok"),
        "मैप" to listOf("maps"), "نقشہ" to listOf("maps"),
        "जीमेल" to listOf("gmail"),
        "कैलकुलेटर" to listOf("calculator"), "कैलक्युलेटर" to listOf("calculator"),
        "घड़ी" to listOf("clock"), "क्लॉक" to listOf("clock"), "گھڑی" to listOf("clock"),
        "प्ले स्टोर" to listOf("play store"), "प्लेस्टोर" to listOf("play store"),
        "फोन" to listOf("phone", "dialer"),
        "मैसेज" to listOf("messages", "message"),
        "कॉन्टैक्ट" to listOf("contacts"),
        "ट्विटर" to listOf("x", "twitter"),
        "गूगल" to listOf("google")
    )

    private fun openApp(name: String): Boolean {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        fun labelOf(ri: ResolveInfo): String = ri.loadLabel(pm).toString().lowercase()

        val key = Text.norm(name)
        val candidates = mutableListOf<String>()
        for ((alias, targets) in aliases) {
            if (key.contains(Text.norm(alias))) candidates.addAll(targets)
        }
        candidates.add(key)

        for (c in candidates) {
            val exact = apps.firstOrNull { labelOf(it) == c }
            val hit = exact ?: apps
                .filter { val l = labelOf(it); l.contains(c) || (l.length >= 3 && c.contains(l)) }
                .minByOrNull { labelOf(it).length }
            if (hit != null) {
                launch(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
                )
                JarvisLog.add("↗ ${labelOf(hit)} khul gaya")
                return true
            }
        }
        JarvisLog.add("✗ App nahi mila: $name")
        return false
    }

    // ---------- YouTube / Google ----------
    private fun youtube(query: String, play: Boolean) {
        // sp=EgIQAQ%253D%253D = sirf "Videos" filter (Shorts/channel hatke pehla result asli video hota hai)
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query) +
            (if (play) "&sp=EgIQAQ%253D%253D" else "")
        try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        if (play) {
            val s = svc
            if (s != null) s.playFirstVideo()
            else JarvisLog.add("ⓘ Video apne-aap chalane ke liye Accessibility service on karein")
        }
    }

    private fun google(q: String) {
        try {
            launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        }
    }

    // ---------- Phone ke systems ----------
    private fun torch(on: Boolean) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: throw IllegalStateException("Is phone mein flash nahi mila")
        cm.setTorchMode(id, on)
    }

    private fun volume(mode: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        when (mode) {
            "up" -> repeat(3) { am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
            "down" -> repeat(3) { am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            "mute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "max" -> am.setStreamVolume(stream, am.getStreamMaxVolume(stream), AudioManager.FLAG_SHOW_UI)
            else -> throw IllegalArgumentException("Unknown volume mode: $mode")
        }
    }

    private fun panel(target: String) {
        val q = Build.VERSION.SDK_INT >= 29
        val action = when (target) {
            "wifi", "internet" -> if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS
            "volume" -> if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        launch(Intent(action))
    }

    private fun alarm(a: Map<String, String>) {
        val h = a["hour"]?.toIntOrNull()
        val m = a["minute"]?.toIntOrNull() ?: 0
        if (h == null) {
            launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return
        }
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, a["label"] ?: "Jarvis")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
    }

    private fun timer(a: Map<String, String>) {
        val secs = a["seconds"]?.toDoubleOrNull()?.toInt() ?: 0
        if (secs <= 0) {
            launch(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            return
        }
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs.coerceAtMost(86400))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
    }
}
