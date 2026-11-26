package com.example.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** Agent kuch bhejne se pehle yahan Haan/Nahi puchta hai. Sirf ek chhota dialog dikhta hai. */
class ConfirmActivity : Activity() {

    companion object {
        @Volatile
        var pending: ((Boolean) -> Unit)? = null
    }

    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val question = intent.getStringExtra("q").orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Jarvis puchta hai")
            .setMessage(question)
            .setCancelable(false)
            .setPositiveButton("Haan, aage badho") { _, _ -> answer(true) }
            .setNegativeButton("Nahi, roko") { _, _ -> answer(false) }
            .show()
    }

    private fun answer(ok: Boolean) {
        if (answered) return
        answered = true
        val cb = pending
        pending = null
        finish()
        cb?.invoke(ok)
    }

    override fun onDestroy() {
        // Dialog kisi aur tarah band ho gaya to "nahi" maana jayega
        if (!answered) answer(false)
        super.onDestroy()
    }
}
