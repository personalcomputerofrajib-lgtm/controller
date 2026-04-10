package com.wifimonitor.data

/**
 * Level 8: Fault Investigation Model.
 * Encapsulates a detected network issue in a user-decision format.
 */
data class NetworkFault(
    val id: String,
    val type: FaultType,
    val title: String,
    val fact: String,           // E.g., "Average ping increased to 280 ms"
    val relatedData: List<Metric>, // E.g., Ping: 280ms, Loss: 2%
    val context: String? = null,
    val actions: List<FaultAction>,
    val severity: Int           // 0=Info, 1=Warning, 2=Danger
)

enum class FaultType {
    LATENCY, BANDWIDTH, NEW_DEVICE, SECURITY, CONGESTION
}

data class Metric(val label: String, val value: String)

data class FaultAction(val label: String, val route: String? = null, val isPrimary: Boolean = false)
