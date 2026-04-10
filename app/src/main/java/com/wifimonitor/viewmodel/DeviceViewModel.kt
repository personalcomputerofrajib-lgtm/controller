package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.data.*
import com.wifimonitor.scanner.PortScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val device: NetworkDevice? = null,
    val trafficRecords: List<TrafficRecord> = emptyList(),
    val isPortScanning: Boolean = false,
    val editingNickname: Boolean = false,
    val nicknameInput: String = "",
    val isPaused: Boolean = false,
    val rules: List<com.wifimonitor.rules.RuleEngine.Rule> = emptyList()
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val portScanner: PortScanner,
    private val ruleEngine: com.wifimonitor.rules.RuleEngine,
    private val interventionEngine: com.wifimonitor.network.ActiveInterventionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    fun loadDevice(mac: String) {
        viewModelScope.launch {
            repository.allDevices.collect { devices ->
                val device = devices.firstOrNull { it.mac == mac }
                _uiState.update { it.copy(device = device, nicknameInput = device?.nickname ?: "") }
            }
        }
        viewModelScope.launch {
            repository.getDeviceTraffic(mac).collect { records ->
                _uiState.update { it.copy(trafficRecords = records) }
            }
        }
        observeRules(mac)
    }

    private fun observeRules(mac: String) {
        viewModelScope.launch {
            // Simplified: in a real app, RuleEngine would have a Flow of rules
            val deviceRules = ruleEngine.getRules().filter { it.deviceMac == mac }
            val isPaused = deviceRules.any { it.type == com.wifimonitor.rules.RuleEngine.RuleType.GLOBAL_BLOCK && it.isEnabled }
            _uiState.update { it.copy(rules = deviceRules, isPaused = isPaused) }
        }
    }

    fun runPortScan() {
        val ip = _uiState.value.device?.ip ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPortScanning = true) }
            val result = portScanner.scanDevice(ip)
            val device = _uiState.value.device ?: return@launch
            val updated = device.copy(openPorts = result.openPorts.joinToString(","))
            repository.setDeviceOpenPorts(device.mac, updated.openPorts)
            _uiState.update { it.copy(isPortScanning = false) }
        }
    }

    fun startEditNickname() = _uiState.update { it.copy(editingNickname = true) }
    fun updateNicknameInput(v: String) = _uiState.update { it.copy(nicknameInput = v) }

    fun saveNickname() {
        val mac = _uiState.value.device?.mac ?: return
        val name = _uiState.value.nicknameInput.trim()
        viewModelScope.launch {
            repository.setDeviceNickname(mac, name)
            _uiState.update { it.copy(editingNickname = false) }
        }
    }

    fun toggleBlock() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            if (device.isBlocked) {
                interventionEngine.liftQuarantine(device.mac)
            } else {
                // For ARP spoofing we need gateway, but the engine usually 
                // obtains this or we stick to DNS-based block if gateway is unknown.
                interventionEngine.quarantineDevice(device.mac, device.ip, "192.168.1.1")
            }
        }
    }

    fun toggleTracked() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            repository.setDeviceTracked(device.mac, !device.isTracked)
        }
    }

    fun togglePauseInternet() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            if (_uiState.value.isPaused) {
                interventionEngine.liftQuarantine(device.mac)
            } else {
                interventionEngine.quarantineDevice(device.mac, device.ip, "192.168.1.1")
            }
            observeRules(device.mac)
        }
    }
}
