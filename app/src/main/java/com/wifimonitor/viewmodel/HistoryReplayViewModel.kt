package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Killer Feature #2: Forensic Timeline Replay.
 * Shifts the entire application state to a specific point in time.
 */
@HiltViewModel
class HistoryReplayViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    data class ReplayState(
        val devicesAtTime: List<DeviceHistory> = emptyList(),
        val selectedTimestamp: Long = 0,
        val isLoadingSnapshot: Boolean = false,
        val availableRange: ClosedRange<Long> = 0L..0L
    )

    private val _state = MutableStateFlow(ReplayState())
    val state: StateFlow<ReplayState> = _state.asStateFlow()

    fun updateReplayTime(timestamp: Long) {
        _state.update { it.copy(selectedTimestamp = timestamp, isLoadingSnapshot = true) }
        viewModelScope.launch {
            // Note: In a real app we'd fetch all devices' history nearest to this timestamp
            // For now, we simulate by fetching the most recent history batch
            val snapshots = mutableListOf<DeviceHistory>()
            val onlineMacs = repository.allDevices.first().map { it.mac }
            
            onlineMacs.forEach { mac ->
                val history = repository.getDeviceHistory(mac, 50)
                val nearest = history.minByOrNull { Math.abs(it.timestamp - timestamp) }
                if (nearest != null && Math.abs(nearest.timestamp - timestamp) < 600000) { // Within 10 mins
                    snapshots.add(nearest)
                }
            }
            
            _state.update { it.copy(devicesAtTime = snapshots, isLoadingSnapshot = false) }
        }
    }

    fun initRange(startTime: Long, endTime: Long) {
        _state.update { it.copy(availableRange = startTime..endTime, selectedTimestamp = endTime) }
    }
}
