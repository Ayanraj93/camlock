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

    fun shizukuReady(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    // Shizuku ke zariye shell command chalao
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

    // enabled=true -> camera chalu (privacy block hatao), false -> camera band
    fun setCamera(c: Context, enabled: Boolean): Boolean {
        if (!shizukuReady() || !hasPermission()) {
            log(c, "Shizuku ready nahi")
            return false
        }
        return try {
            val verb = if (enabled) "disable" else "enable"
            val (code, out) = sh("cmd sensor_privacy $verb 0 camera 2>&1")
            if (code != 0) {
                log(c, "Error: $out")
                false
            } else {
                prefs(c).edit().putBoolean("on", enabled).apply()
                log(c, if (enabled) "Camera ON" else "Camera OFF")
                true
            }
        } catch (e: Throwable) {
            log(c, "Error: ${e.message}")
            false
        }
    }

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

    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
        val old = (prefs(c).getString("log", "") ?: "")
            .lines().filter { it.isNotBlank() }.take(29)
        prefs(c).edit().putString("log", (listOf("$t  $msg") + old).joinToString("\n")).apply()
    }

    fun logText(c: Context): String = prefs(c).getString("log", "") ?: ""

    fun lastLine(c: Context): String = logText(c).lines().firstOrNull() ?: ""
}
