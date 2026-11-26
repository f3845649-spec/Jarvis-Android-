package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Accessibility service: dusre apps ki screen padhta hai aur un par tap / type karta hai.
 * User isse Settings > Accessibility mein khud on karta hai.
 * Background se apps kholne ki ijazat bhi isi service ke zariye milti hai.
 */
class JarvisService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastNodes: List<AccessibilityNodeInfo> = emptyList()
    private var lastLineText = ""
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        instance = this
        tts = TextToSpeech(this) { status -> ttsReady = (status == TextToSpeech.SUCCESS) }
        JarvisLog.add("● Accessibility service on hai")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- System buttons ----------
    fun globalKey(key: String) {
        val action = when (key) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            else -> -1
        }
        if (action < 0 || !performGlobalAction(action)) {
            JarvisLog.add("✗ System button fail hua: $key")
        }
    }

    // ---------- Screen par tap / type ----------
    /** Screen par jo text/label dikh raha hai usse dhoondhkar tap karta hai (6 second tak koshish). */
    fun clickText(text: String) {
        if (text.isBlank()) return
        poll(
            initialMs = 800L,
            timeoutMs = 6000L,
            everyMs = 600L,
            onTimeout = { JarvisLog.add("✗ Screen par nahi mila: $text") },
            attempt = {
                val root = rootInActiveWindow
                val node = if (root == null) null else findNode(root) { n ->
                    (n.text?.toString()?.contains(text, ignoreCase = true) == true) ||
                        (n.contentDescription?.toString()?.contains(text, ignoreCase = true) == true)
                }
                if (node == null) {
                    false
                } else {
                    if (!clickUp(node)) tapCenter(node)
                    JarvisLog.add("👆 Tap kiya: $text")
                    true
                }
            }
        )
    }

    /** Jo input box focus mein hai (ya pehla editable box) usme text daalta hai. */
    fun typeText(text: String) {
        val root = rootInActiveWindow ?: throw IllegalStateException("Screen padh nahi paya")
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findNode(root) { it.isEditable }
            ?: throw IllegalStateException("Koi input box nahi mila")
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            throw IllegalStateException("Text type nahi ho paya")
        }
    }

    /**
     * YouTube ke search results khulne ke baad pehli video par tap karta hai.
     * Tarika: screen par sabse upar wala bada (chaudai >= 60%) clickable item jiski description ho.
     * Language-independent hai, par YouTube ka layout badle to kaam na kare (best effort).
     */
    fun playFirstVideo() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        poll(
            initialMs = 4500L,
            timeoutMs = 14000L,
            everyMs = 800L,
            onTimeout = { JarvisLog.add("✗ Pehli video nahi mili — khud tap karein") },
            attempt = {
                val root = rootInActiveWindow
                val pkg = root?.packageName?.toString().orEmpty()
                if (root == null || pkg == packageName || pkg.contains("launcher") || pkg.contains("systemui")) {
                    false
                } else {
                    var best: AccessibilityNodeInfo? = null
                    var bestTop = Int.MAX_VALUE
                    val r = Rect()
                    walkAll(root) { n ->
                        if (n.isClickable && !n.contentDescription.isNullOrBlank()) {
                            n.getBoundsInScreen(r)
                            if (r.width() >= w * 0.6 && r.height() >= h * 0.10 && r.top >= h * 0.12 && r.top < bestTop) {
                                best = n
                                bestTop = r.top
                            }
                        }
                    }
                    val b = best
                    if (b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        JarvisLog.add("▶ Pehli video chalayi")
                        true
                    } else {
                        false
                    }
                }
            }
        )
    }


    // ---------- Agent ke liye: screen padhna aur numbered elements par kaam ----------
    /**
     * Abhi screen par jo dikh raha hai uski chhoti text list banata hai. Har line: id|flags|text
     * flags: c = tap ho sakta hai, e = text field, s = scroll hota hai, f = focused, k = checked.
     * Password field ka text kabhi nahi bheja jata.
     */
    fun snapshot(): String {
        val root = rootInActiveWindow ?: return "(screen padh nahi paya)"
        val pkg = root.packageName?.toString().orEmpty()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val sb = StringBuilder("App: $pkg\n")
        if (pkg == packageName) sb.append("(Jarvis ka apna app screen par hai)\n")
        lastLineText = ""
        collect(root, nodes, sb)
        lastNodes = nodes
        if (nodes.isEmpty()) sb.append("(koi readable element nahi mila)\n")
        return sb.toString()
    }

    private fun collect(n: AccessibilityNodeInfo, nodes: MutableList<AccessibilityNodeInfo>, sb: StringBuilder) {
        if (nodes.size >= 70 || !n.isVisibleToUser) return
        val dm = resources.displayMetrics
        val raw = n.text?.toString() ?: n.contentDescription?.toString() ?: ""
        var text = raw.replace('\n', ' ').trim().take(90)
        if (n.isPassword) text = "(password field)"
        val tappable = n.isClickable || hasClickableParent(n)
        val show = text.isNotEmpty() || n.isEditable || n.isScrollable || n.isClickable
        if (show && !(text.isNotEmpty() && text == lastLineText && !n.isEditable)) {
            if (text.isEmpty()) {
                val r = Rect()
                n.getBoundsInScreen(r)
                val px = (r.centerX() * 100 / dm.widthPixels)
                val py = (r.centerY() * 100 / dm.heightPixels)
                text = "(no label @$px%,$py%)"
            }
            val flags = StringBuilder()
            if (tappable) flags.append('c')
            if (n.isEditable) flags.append('e')
            if (n.isScrollable) flags.append('s')
            if (n.isFocused) flags.append('f')
            if (n.isChecked) flags.append('k')
            sb.append(nodes.size).append('|').append(flags.toString()).append('|').append(text).append('\n')
            nodes.add(n)
            lastLineText = text
        }
        for (i in 0 until n.childCount) {
            val child = n.getChild(i) ?: continue
            collect(child, nodes, sb)
        }
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    /** snapshot() ke id wale element ka label (text ya description). */
    fun labelOf(id: Int): String {
        val n = lastNodes.getOrNull(id) ?: return ""
        return (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
    }

    /** snapshot() mein jis id wale element par tap karna hai. */
    fun tapId(id: Int): Boolean {
        val n = lastNodes.getOrNull(id) ?: return false
        if (!clickUp(n)) tapCenter(n)
        return true
    }

    /** id wale text field mein text daalta hai (id na ho to focused/pehla field). Field na ho to pehle usse tap karta hai. */
    fun typeInto(id: Int?, text: String): Boolean {
        val byId = if (id != null) lastNodes.getOrNull(id) else null
        val editableById = if (byId != null && byId.isEditable) byId else null
        if (byId != null && editableById == null) clickUp(byId)
        val root = rootInActiveWindow
        val node = editableById
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: (if (root != null) findNode(root) { it.isEditable } else null)
            ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Keyboard ka Enter / Search dabata hai (Android 11+). */
    fun enter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** down = niche ka content dikhao. */
    fun scroll(down: Boolean) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = dm.heightPixels * (if (down) 0.72f else 0.30f)
        val y2 = dm.heightPixels * (if (down) 0.30f else 0.72f)
        val path = Path()
        path.moveTo(x, y1)
        path.lineTo(x, y2)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 350L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** Jawab user ko dikhata (Toast) aur bolkar sunata (TTS) hai. */
    fun announce(text: String) {
        if (text.isBlank()) return
        handler.post { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    // ---------- Helpers ----------
    private fun poll(
        initialMs: Long,
        timeoutMs: Long,
        everyMs: Long,
        onTimeout: () -> Unit,
        attempt: () -> Boolean
    ) {
        val end = SystemClock.uptimeMillis() + initialMs + timeoutMs
        val task = object : Runnable {
            override fun run() {
                val done = try {
                    attempt()
                } catch (e: Exception) {
                    false
                }
                if (done) return
                if (SystemClock.uptimeMillis() >= end) {
                    onTimeout()
                    return
                }
                handler.postDelayed(this, everyMs)
            }
        }
        handler.postDelayed(task, initialMs)
    }

    private fun findNode(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, match)
            if (found != null) return found
        }
        return null
    }

    private fun walkAll(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walkAll(child, visit)
        }
    }

    /** Node ya uske kisi parent par click (kai baar text wala node khud clickable nahi hota). */
    private fun clickUp(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    private fun tapCenter(node: AccessibilityNodeInfo) {
        val r = Rect()
        node.getBoundsInScreen(r)
        val path = Path()
        path.moveTo(r.exactCenterX(), r.exactCenterY())
        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
