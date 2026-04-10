package com.wifimonitor.data

import androidx.room.*

/**
 * Point-in-time snapshot stored for historical graphing.
 * One row per device per scan cycle (throttled to every ~5 min to avoid DB bloat).
 */
@Entity(
    tableName = "device_history",
    indices = [
        Index(value = ["mac"]),
        Index(value = ["timestamp"])
    ]
)
data class DeviceHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mac: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Int = -1,
    val activityScore: Float = 0f,   // 0.0 - 1.0
    val reliabilityPct: Int = 0,
    val status: DeviceStatus = DeviceStatus.ONLINE,
    val jitterMs: Int = 0
)
