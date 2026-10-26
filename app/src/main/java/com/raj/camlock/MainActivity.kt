package com.raj.camlock

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private var resetMode = false

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

    private fun bioAuth(title: String, onOk: () -> Unit) {
        val bm = getSystemService(BiometricManager::class.java)
        val can = if (Build.VERSION.SDK_INT >= 30)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        else
            @Suppress("DEPRECATION") bm.canAuthenticate()
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            toast("Fingerprint available nahi hai ya setup nahi hai")
            return
        }
        val exec = mainExecutor
        val builder = BiometricPrompt.Builder(this)
            .setTitle(title)
            .setNegativeButton("Cancel", exec) { _, _ -> }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        builder.build().authenticate(
            CancellationSignal(), exec,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    toast(errString?.toString() ?: "Fingerprint error")
                }
            }
        )
    }

    private fun startSession(mins: Int) {
        if (!Policy.setCamera(this, true)) {
            toast("Camera ON nahi hua: " + Policy.lastLine(this))
            render()
            return
        }
        startForegroundService(
            Intent(this, CamService::class.java)
                .putExtra("endAt", System.currentTimeMillis() + mins * 60_000L)
        )
        render()
    }

    private fun passwordSetupScreen(title: String) {
        root.addView(tv(title))
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
                    resetMode = false
                    toast("Password save ho gaya")
                    render()
                }
            }
        })
    }

    private fun render() {
        val sv = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }
        sv.addView(root)
        setContentView(sv)
        root.addView(tv("CamLock", 28f))

        // 1) Device Admin Check
        if (!Policy.isAdmin(this)) {
            root.addView(tv("Camera lock karne ke liye Device Admin permission chahiye."))
            root.addView(btn("Device Admin Activate Karo") {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Policy.admin(this@MainActivity))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Camera lock/unlock karne ke liye zaruri hai")
                }
                startActivity(intent)
            })
            return
        }

        // 2) Password setup ya reset
        if (resetMode) {
            passwordSetupScreen("Fingerprint verified. Naya password set karo.")
            return
        }
        if (!Policy.hasPassword(this)) {
            Policy.setCamera(this, false)
            passwordSetupScreen("Pehle CamLock password banayein (kam se kam 4 akshar).")
            return
        }

        // 3) Main screen
        val on = Policy.isOn(this)
        root.addView(tv(if (on) "Camera: ON (Khula hai)" else "Camera: OFF (Blocked)", 22f))

        val pw = edit("Password", true)
        val mins = edit("Kitne minute kholna hai (1 se 240)", false)
        mins.setText("5")
        root.addView(pw)
        root.addView(mins)

        fun minutes() = (mins.text.toString().toIntOrNull() ?: 5).coerceIn(1, 240)

        root.addView(btn("Camera ON karo (password)") {
            if (!Policy.check(this, pw.text.toString())) {
                toast("Galat password")
                return@btn
            }
            pw.setText("")
            startSession(minutes())
        })

        root.addView(btn("Camera ON karo (fingerprint)") {
            val m = minutes()
            bioAuth("Camera kholne ke liye scan karein") { startSession(m) }
        })

        root.addView(btn("Camera Abhi OFF karo") {
            Policy.setCamera(this, false)
            startService(Intent(this, CamService::class.java).setAction(CamService.ACTION_STOP))
            render()
        })

        root.addView(btn("Password bhool gaye? Fingerprint se reset") {
            bioAuth("Password reset karne ke liye") {
                resetMode = true
                render()
            }
        })

        root.addView(tv("\nActivity History:", 16f))
        root.addView(tv(Policy.logText(this).ifBlank { "Khali hai" }, 13f))
    }
}
