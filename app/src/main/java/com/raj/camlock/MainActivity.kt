package com.raj.camlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

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

        // 1) Device Owner check
        if (!Policy.isOwner(this)) {
            root.addView(
                tv(
                    "Ye app abhi Device Owner nahi hai. Termux me ye chalao:\n\n" +
                        "adb shell dpm set-device-owner com.raj.camlock/.AdminReceiver\n\n" +
                        "(Pehle phone se saare accounts hata do.)"
                )
            )
            return
        }

        // 2) Pehli baar password set karo
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

        // 3) Main screen
        val on = Policy.isCameraEnabled(this)
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
            Policy.setCamera(this, true)
            startForegroundService(
                Intent(this, CamService::class.java)
                    .putExtra("endAt", System.currentTimeMillis() + m * 60_000L)
            )
            pw.setText("")
            render()
        })

        root.addView(btn("Camera OFF karo") {
            Policy.setCamera(this, false)
            startService(Intent(this, CamService::class.java).setAction(CamService.ACTION_STOP))
            render()
        })

        root.addView(tv("\nRecord (naya upar):", 16f))
        root.addView(tv(Policy.logText(this).ifBlank { "abhi kuch nahi" }, 13f))

        root.addView(View(this).apply { minimumHeight = dp(24) })
        root.addView(btn("Device Owner hatao (password chahiye)") {
            if (!Policy.check(this, pw.text.toString())) {
                toast("Upar password daalo, phir dabao")
                return@btn
            }
            startService(Intent(this, CamService::class.java).setAction(CamService.ACTION_STOP))
            Policy.clearOwner(this)
            toast("Device Owner hat gaya, camera normal")
            render()
        })
    }
}
