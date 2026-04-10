package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleUiState(
    val rules: List<AlertRule> = emptyList(),
    val devicesForRules: List<NetworkDevice> = emptyList()
)

@HiltViewModel
class RuleViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleUiState())
    val uiState: StateFlow<RuleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.allRules, repository.allDevices) { rules, devices ->
                RuleUiState(rules, devices)
            }.collect { _uiState.value = it }
        }
    }

    fun addRule(mac: String, name: String, type: RuleType, threshold: Long = 0) {
        viewModelScope.launch {
            repository.addAlertRule(AlertRule(
                deviceMac = mac,
                deviceName = name,
                type = type,
                thresholdValue = threshold
            ))
        }
    }

    fun deleteRule(rule: AlertRule) {
        viewModelScope.launch {
            repository.deleteAlertRule(rule)
        }
    }

    fun toggleRule(rule: AlertRule) {
        viewModelScope.launch {
            repository.addAlertRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }
}
