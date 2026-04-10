package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForensicsUiState(
    val topUsers: List<UserBandwidth> = emptyList(),
    val trafficHistory: List<TrafficPoint> = emptyList(),
    val classification: Map<String, Float> = emptyMap(), // Domain Category -> Percentage
    val totalDownload: Long = 0L,
    val totalUpload: Long = 0L
)

data class UserBandwidth(val name: String, val mac: String, val bytes: Long, val percent: Float)
data class TrafficPoint(val value: Float)

@HiltViewModel
class ForensicsViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForensicsUiState())
    val uiState: StateFlow<ForensicsUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                repository.allDevices,
                repository.recentTraffic
            ) { devices, traffic ->
                val totalBytes = devices.sumOf { it.totalDownloadBytes + it.totalUploadBytes }.coerceAtLeast(1L)
                
                // 1. Top Users
                val sorted = devices
                    .filter { it.totalDownloadBytes + it.totalUploadBytes > 0 }
                    .sortedByDescending { it.totalDownloadBytes + it.totalUploadBytes }
                    .take(10)
                    .map { d ->
                        val bytes = d.totalDownloadBytes + d.totalUploadBytes
                        UserBandwidth(d.displayName, d.mac, bytes, bytes.toFloat() / totalBytes)
                    }

                // 2. Traffic Classification (Simple heuristic by domain)
                val categoryMap = traffic.groupBy { deriveCategory(it.domain) }
                    .mapValues { it.value.size.toFloat() / traffic.size.coerceAtLeast(1) }

                // 3. Fake Graph Data for now (Simulated from history count)
                // In a real app we'd query the DeviceHistory table via repository
                val mockPoints = List(12) { (0.2f + (Math.random() * 0.6f).toFloat()) }

                _uiState.update { it.copy(
                    topUsers = sorted,
                    classification = categoryMap,
                    trafficHistory = mockPoints.map { TrafficPoint(it) },
                    totalDownload = devices.sumOf { it.totalDownloadBytes },
                    totalUpload = devices.sumOf { it.totalUploadBytes }
                )}
            }.collect()
        }
    }

    private fun deriveCategory(domain: String): String {
        return when {
            domain.contains("google") || domain.contains("akamai") || domain.contains("cdn") -> "System"
            domain.contains("netflix") || domain.contains("youtube") || domain.contains("twitch") -> "Streaming"
            domain.contains("steam") || domain.contains("xbox") || domain.contains("playstation") -> "Gaming"
            domain.contains("facebook") || domain.contains("instagram") || domain.contains("tiktok") -> "Social"
            else -> "Other"
        }
    }
}
