package com.wifimonitor.analyzer

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wifimonitor.data.DeviceRepository
import com.wifimonitor.data.DeviceStatus
import com.wifimonitor.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DeviceRepository
) {
    private val tag = "PresenceManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ALERT_ID = "wifi_alert_channel"
    
    // Tracks the last known status of each device to detect transitions
    private val lastKnownStatus = mutableMapOf<String, DeviceStatus>()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            repository.allDevices.collectLatest { devices ->
                devices.forEach { device ->
                    if (!device.isTracked) {
                        lastKnownStatus.remove(device.mac)
                        return@forEach
                    }

                    val previousStatus = lastKnownStatus[device.mac]
                    val currentStatus = device.status

                    if (previousStatus != null && previousStatus != currentStatus) {
                        handleStatusChange(device.mac, device.displayName, currentStatus)
                    }
                    
                    lastKnownStatus[device.mac] = currentStatus
                }
            }
        }
    }

    private fun handleStatusChange(mac: String, name: String, status: DeviceStatus) {
        val message = when (status) {
            DeviceStatus.ONLINE -> "$name has connected to the network."
            DeviceStatus.OFFLINE -> "$name has disconnected."
            else -> return
        }

        Log.i(tag, "Presence Alert: $message")
        sendNotification(name, message)
    }

    private fun sendNotification(title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            context, 
            0, 
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setContentTitle("Presence Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery) // Using a standard icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
