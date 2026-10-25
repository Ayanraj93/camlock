package com.raj.camlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class CamService : Service() {

    companion object {
        const val ACTION_STOP = "com.raj.camlock.STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Policy.log(c, "Screen off -> auto OFF")
            finish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val endAt = intent?.getLongExtra("endAt", 0L) ?: 0L
        val left = endAt - System.currentTimeMillis()
        if (left <= 0) {
            finish()
            return START_NOT_STICKY
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("cam", "Camera session", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, CamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Abhi OFF", stop
        ).build()

        val n = Notification.Builder(this, "cam")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Camera ON hai")
            .setContentText("Timer khatam hote hi apne aap band ho jayega")
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(action)
            .build()

        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        if (!registered) {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registered = true
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            Policy.log(this, "Timer khatam -> auto OFF")
            finish()
        }, left)
        return START_NOT_STICKY
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        Policy.setCamera(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (registered) {
            unregisterReceiver(screenOff)
            registered = false
        }
        // Service kisi bhi wajah se band ho, camera band hi rahe
        if (Policy.isOn(this)) Policy.setCamera(this, false)
        super.onDestroy()
    }
}
