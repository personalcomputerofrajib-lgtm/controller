package com.wifimonitor.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_records",
    indices = [
        Index(value = ["deviceMac", "timestamp"]), // Audit 8: Forensic Replay Index
        Index(value = ["timestamp"]),              // Global sorting
        Index(value = ["domain"])                 // Stats lookup
    ]
)
data class TrafficRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceMac: String,
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val queryType: String = "A",       // DNS record type
    val source: String = "DNS",        // DNS, HTTP, SNI
    val isBlocked: Boolean = false,
    val ruleId: String? = null,
    val category: String = "Unknown",  // Streaming, Gaming, Browsing, IoT
    val bandwidthBytes: Long = 0L
)
