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
    private fun dpm(c: Context) =
        c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)
    private fun prefs(c: Context) = c.getSharedPreferences("camlock", Context.MODE_PRIVATE)

    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    fun isCameraEnabled(c: Context) =
        !isOwner(c) || !dpm(c).getCameraDisabled(admin(c))

    fun setCamera(c: Context, enabled: Boolean): Boolean {
        if (!isOwner(c)) return false
        dpm(c).setCameraDisabled(admin(c), !enabled)
        log(c, if (enabled) "Camera ON" else "Camera OFF")
        return true
    }

    fun clearOwner(c: Context) {
        if (!isOwner(c)) return
        dpm(c).setCameraDisabled(admin(c), false)
        @Suppress("DEPRECATION")
        dpm(c).clearDeviceOwnerApp(c.packageName)
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
}
