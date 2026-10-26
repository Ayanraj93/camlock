package com.raj.camlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Reboot ke baad safe default: camera band
        Policy.setCamera(context, false)
    }
}
