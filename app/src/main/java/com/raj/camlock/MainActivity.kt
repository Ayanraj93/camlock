package com.raj.camlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float = 16f) = TextView(this).apply {
        text = t
        textSize = size
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }

    private fun edit(h: String, pw: Boolean) = EditText(this).apply {
        hint = h
        inputType = if (pw)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_NUMBER
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun render() {
        val sv = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }
        sv.addView(root)
        setContentView(sv)
        root.addView(tv("CamLock", 28f))

        // 1) Shizuku chal raha hai?
        if (!Policy.shizukuReady()) {
            root.addView(
                tv(
                    "Shizuku chalu nahi hai.\n\nShizuku app kholo -> " +
                        "\"Start via Wireless debugging\" -> phir yahan wapas aao."
                )
            )
            root.addView(btn("Dobara check karo") { render() })
            return
        }

        // 2) Shizuku permission
        if (!Policy.hasPermission()) {
            root.addView(tv("CamLock ko Shizuku ki permission chahiye."))
            root.addView(btn("Shizuku permission do") {
                try { Shizuku.requestPermission(100) } catch (e: Throwable) { toast("Error: ${e.message}") }
            })
            root.addView(btn("Permission de di, dobara check karo") { render() })
            return
        }

        // 3) Pehli baar password
        if (!Policy.hasPassword(this)) {
            root.addView(tv("Pehle apna password set karo (kam se kam 4 akshar)."))
            val p1 = edit("Naya password", true)
            val p2 = edit("Password dobara", true)
            root.addView(p1)
            root.addView(p2)
            root.addView(btn("Password save karo") {
                val a = p1.text.toString()
                val b = p2.text.toString()
                when {
                    a.length < 4 -> toast("Kam se kam 4 akshar chahiye")
                    a != b -> toast("Dono password alag hain")
                    else -> {
                        Policy.setPassword(this, a)
                        Policy.setCamera(this, false)
                        render()
                    }
                }
            })
            return
        }

        // 4) Main screen
        val on = Policy.isOn(this)
        root.addView(tv(if (on) "Camera: ON" else "Camera: OFF (band)", 22f))

        val pw = edit("Password", true)
        val mins = edit("Kitne minute (1 se 240)", false)
        mins.setText("5")
        root.addView(pw)
        root.addView(mins)

        root.addView(btn("Camera ON karo") {
            if (!Policy.check(this, pw.text.toString())) {
                toast("Galat password")
                return@btn
            }
            val m = (mins.text.toString().toIntOrNull() ?: 5).coerceIn(1, 240)
            if (!Policy.setCamera(this, true)) {
                toast("Camera ON nahi hua: " + Policy.lastLine(this))
                render()
                return@btn
            }
            startForegroundService(
                Intent(this, CamService::class.java)
                    .putExtra("endAt", System.currentTimeMillis() + m * 60_000L)
            )
            pw.setText("")
            render()
        })

        root.addView(btn("Camera OFF karo") {
            if (!Policy.setCamera(this, false)) toast("Error: " + Policy.lastLine(this))
            startService(Intent(this, CamService::class.java).setAction(CamService.ACTION_STOP))
            render()
        })

        root.addView(tv("\nRecord (naya upar):", 16f))
        root.addView(tv(Policy.logText(this).ifBlank { "abhi kuch nahi" }, 13f))
    }
}
