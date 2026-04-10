package com.wifimonitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "android.net.wifi.STATE_CHANGE" ||
            action == "android.net.conn.CONNECTIVITY_CHANGE") {
            
            try {
                // Enterprise Integrity: Always-on start loop
                MonitorService.start(context)
            } catch (e: Exception) {
                // Non-fatal if system blocks background start (foreground service handles this)
                android.util.Log.e("BootReceiver", "Service Auto-Start: ${e.message}")
            }
        }
    }
}
