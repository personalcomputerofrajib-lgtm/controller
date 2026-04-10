package com.wifimonitor.data

import androidx.room.*

enum class RuleType {
    THRESHOLD_MB,    // Data limit in MB
    DEVICE_DISCONNECT,
    DEVICE_RECONNECT
}

@Entity(tableName = "alert_rules")
data class AlertRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceMac: String,
    val deviceName: String,
    val type: RuleType,
    val thresholdValue: Long = 0, // Used for THRESHOLD_MB
    val isEnabled: Boolean = true,
    val lastTriggered: Long = 0
)
