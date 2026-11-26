package com.example.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var runBtn: Button
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var aiSwitch: Switch
    private lateinit var confirmSwitch: Switch
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText

    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        runBtn = findViewById(R.id.run)
        statusView = findViewById(R.id.status)
        logView = findViewById(R.id.log)
        aiSwitch = findViewById(R.id.ai)
        confirmSwitch = findViewById(R.id.confirm)
        keyInput = findViewById(R.id.key)
        modelInput = findViewById(R.id.model)

        aiSwitch.isChecked = prefs.getBoolean("ai", false)
        confirmSwitch.isChecked = prefs.getBoolean("confirm", true)
        keyInput.setText(prefs.getString("key", ""))
        modelInput.setText(prefs.getString("model", DEFAULT_MODEL))

        aiSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !prefs.getBoolean("privacy_ack", false)) showPrivacyNotice()
        }
        findViewById<Button>(R.id.a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.stop).setOnClickListener { Agent.cancel() }
        runBtn.setOnClickListener { submit() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        logView.text = JarvisLog.text()
        JarvisLog.listener = { runOnUiThread { logView.text = JarvisLog.text() } }
    }

    override fun onPause() {
        JarvisLog.listener = null
        savePrefs()
        super.onPause()
    }

    private fun showPrivacyNotice() {
        AlertDialog.Builder(this)
            .setTitle("Zaroori jaankari")
            .setMessage(
                "Agent mode har step par phone ki screen ka text (chats, naam, numbers jo bhi screen par dikhe) " +
                    "Anthropic ke API server ko bhejta hai taaki AI samajh sake aage kya karna hai.\n\n" +
                    "Bank, OTP, passwords ya private screens par isse use na karein."
            )
            .setPositiveButton("Samajh gaya") { _, _ -> prefs.edit().putBoolean("privacy_ack", true).apply() }
            .setNegativeButton("Band karo") { _, _ -> aiSwitch.isChecked = false }
            .setCancelable(false)
            .show()
    }

    private fun refreshStatus() {
        statusView.text = if (JarvisService.instance != null) {
            "✓ Accessibility service ON - poora control available"
        } else {
            "✗ Accessibility service OFF - Agent mode, video tap, back/home wagairah nahi chalenge"
        }
    }

    private fun savePrefs() {
        prefs.edit()
            .putBoolean("ai", aiSwitch.isChecked)
            .putBoolean("confirm", confirmSwitch.isChecked)
            .putString("key", keyInput.text.toString().trim())
            .putString("model", modelInput.text.toString().trim())
            .apply()
    }

    private fun submit() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        savePrefs()
        JarvisLog.add("▶ $text")

        val key = keyInput.text.toString().trim()
        val model = modelInput.text.toString().trim().ifEmpty { DEFAULT_MODEL }
        val svc = JarvisService.instance

        if (aiSwitch.isChecked && key.isNotEmpty() && svc != null) {
            // Agent mode: screen padho -> AI soche -> karo -> dobara padho
            Agent.start(svc, key, model, text, confirmSwitch.isChecked)
            minimizeAfterStart()
        } else if (aiSwitch.isChecked && key.isNotEmpty()) {
            // Service OFF hai: sirf AI se plan banwakar chalate hain (screen padhe bina)
            JarvisLog.add("ⓘ Accessibility OFF hai, isliye Agent nahi chal sakta; sirf plan-mode chal raha hai")
            runBtn.isEnabled = false
            Thread {
                val result = AiPlanner.plan(key, model, text)
                runOnUiThread {
                    runBtn.isEnabled = true
                    val plan = result.getOrNull()
                    if (plan != null) {
                        execute(plan)
                    } else {
                        JarvisLog.add("✗ AI error: ${result.exceptionOrNull()?.message}")
                        execute(Plan(CommandParser.parse(text)))
                    }
                }
            }.start()
        } else {
            if (aiSwitch.isChecked) JarvisLog.add("ⓘ AI mode on hai par API key khali hai — basic parser use ho raha hai")
            execute(Plan(CommandParser.parse(text)))
        }
    }

    private fun execute(plan: Plan) {
        if (plan.say.isNotBlank()) JarvisLog.add("💬 ${plan.say}")
        if (plan.steps.isEmpty()) {
            JarvisLog.add("✗ Samajh nahi aaya. AI mode on karein ya command alag tarah se likhein.")
            return
        }
        plan.steps.forEach { JarvisLog.add("• ${it.describe()}") }
        val svc = JarvisService.instance
        Executor(svc ?: applicationContext, svc).run(plan.steps)
        minimizeAfterStart()
    }

    private fun minimizeAfterStart() {
        input.setText("")
        Handler(Looper.getMainLooper()).postDelayed({ moveTaskToBack(true) }, 400L)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
