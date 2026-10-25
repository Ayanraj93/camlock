package com.raj.camlock

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import rikka.shizuku.Shizuku

object Policy {
    private fun prefs(c: Context) = c.getSharedPreferences("camlock", Context.MODE_PRIVATE)

    // ---------- Shizuku ----------
    fun shizukuReady(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun usable() = shizukuReady() && hasPermission()

    private fun sh(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        return code to out.trim()
    }

    fun isOn(c: Context) = prefs(c).getBoolean("on", false)

    // ---------- Guard: camera band rakhne wala loop (har 1 second) ----------
    // Marker ke [D] ka trick: pkill/pgrep khud ko match nahi karte.
    private const val KILL = "pkill -f 'CAMLOCK_GUAR[D]'"

    @Synchronized
    fun stopGuard() {
        try { sh(KILL) } catch (_: Throwable) {}
    }

    @Synchronized
    fun startGuard(c: Context) {
        if (!usable() || isOn(c)) return
        try {
            sh(KILL)
            sh(
                "nohup sh -c 'while true; do cmd sensor_privacy enable 0 camera " +
                    ">/dev/null 2>&1; sleep 0.3 || sleep 1; done # CAMLOCK_GUARD' " +
                    ">/dev/null 2>&1 </dev/null &"
            )
        } catch (e: Throwable) {
            log(c, "Guard error: ${e.message}")
        }
    }

    fun guardRunning(): Boolean = try {
        usable() && sh("pgrep -f 'CAMLOCK_GUAR[D]'").first == 0
    } catch (e: Throwable) { false }

    // ---------- Camera ON / OFF ----------
    @Synchronized
    fun setCamera(c: Context, enabled: Boolean): Boolean {
        if (!usable()) {
            log(c, "Shizuku ready nahi")
            return false
        }
        return try {
            if (enabled) {
                stopGuard()
                Thread.sleep(300)
            }
            val verb = if (enabled) "disable" else "enable"
            val (code, out) = sh("cmd sensor_privacy $verb 0 camera 2>&1")
            if (code != 0) {
                log(c, "Error: $out")
                if (enabled) startGuard(c)
                false
            } else {
                prefs(c).edit().putBoolean("on", enabled).apply()
                log(c, if (enabled) "Camera ON" else "Camera OFF")
                if (!enabled) startGuard(c)
                true
            }
        } catch (e: Throwable) {
            log(c, "Error: ${e.message}")
            if (enabled) startGuard(c)
            false
        }
    }

    // ---------- Password ----------
    fun hasPassword(c: Context) = prefs(c).contains("hash")

    private fun hash(salt: String, pw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pw).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPassword(c: Context, pw: String) {
        val salt = UUID.randomUUID().toString()
        prefs(c).edit().putString("salt", salt).putString("hash", hash(salt, pw)).apply()
    }

    fun check(c: Context, pw: String): Boolean {
        val p = prefs(c)
        val salt = p.getString("salt", "") ?: ""
        return hash(salt, pw) == p.getString("hash", "")
    }

    // ---------- Log ----------
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
        val old = (prefs(c).getString("log", "") ?: "")
            .lines().filter { it.isNotBlank() }.take(29)
        prefs(c).edit().putString("log", (listOf("$t  $msg") + old).joinToString("\n")).apply()
    }

    fun logText(c: Context): String = prefs(c).getString("log", "") ?: ""

    fun lastLine(c: Context): String = logText(c).lines().firstOrNull() ?: ""
}
