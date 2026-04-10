package com.wifimonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertType { NEW_DEVICE, DEVICE_BLOCKED, ACTIVITY_SPIKE, ANOMALY, STABILITY_DROP, PATTERN_CHANGE, HIDDEN_CAMERA_FOUND, SUSPICIOUS_BEHAVIOR, PORT_CHANGE }

@Entity(tableName = "alerts")
data class AlertRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: AlertType = AlertType.NEW_DEVICE,
    val deviceMac: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
