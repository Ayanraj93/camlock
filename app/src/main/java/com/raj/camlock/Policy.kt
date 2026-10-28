package com.raj.camlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Policy {
    fun dpm(c: Context) =
        c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)
    private fun prefs(c: Context) = c.getSharedPreferences("camlock", Context.MODE_PRIVATE)

    // Check karein ki Device Admin active hai ya nahi
    fun isAdmin(c: Context) = dpm(c).isAdminActive(admin(c))

    // Camera chalu hai?
    fun isOn(c: Context) = isAdmin(c) && !dpm(c).getCameraDisabled(admin(c))

    // enabled=true -> camera chalu, false -> camera band
    fun setCamera(c: Context, enabled: Boolean): Boolean {
        if (!isAdmin(c)) {
            log(c, "Device Admin active nahi hai")
            return false
        }
        return try {
            dpm(c).setCameraDisabled(admin(c), !enabled)
            log(c, if (enabled) "Camera ON" else "Camera OFF")
            true
        } catch (e: Throwable) {
            log(c, "Error: ${e.message}")
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
            .lines().filter { it.isNotBlank() }.take(19)
        prefs(c).edit().putString("log", (listOf("$t  $msg") + old).joinToString("\n")).apply()
    }

    fun logText(c: Context): String = prefs(c).getString("log", "") ?: ""

    fun clearLog(c: Context) {
        prefs(c).edit().remove("log").apply()
    }

    fun lastLine(c: Context): String = logText(c).lines().firstOrNull() ?: ""
}
