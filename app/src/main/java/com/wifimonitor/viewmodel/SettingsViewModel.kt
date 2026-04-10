package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.analyzer.*
import com.wifimonitor.data.*
import com.wifimonitor.network.SecureCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val diagnosticMode: Boolean = false,
    val managedMode: Boolean = false,
    val routerConfigured: Boolean = false,
    val routerHost: String = "",
    val routerHostInput: String = "",
    val routerUserInput: String = "",
    val routerPassInput: String = "",
    val exportResult: String? = null,
    val interfaces: List<InterfaceItem> = emptyList(),
    val diagEntries: List<DiagEntryItem> = emptyList(),
    val efficiencyMode: Boolean = true
)

data class InterfaceItem(val name: String, val type: String, val ip: String, val ssid: String)
data class DiagEntryItem(val category: String, val time: String, val message: String, val details: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: SecureCredentialStore,
    private val diagnostics: DiagnosticLogger,
    private val interfaceMonitor: NetworkInterfaceMonitor,
    private val dataExporter: DataExporter,
    private val repository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())

    init {
        loadState()
        observeInterfaces()
    }

    private fun loadState() {
        _uiState.update {
            it.copy(
                diagnosticMode = credentialStore.isDiagnosticMode(),
                managedMode = credentialStore.isManagedMode(),
                routerHost = credentialStore.getRouterHost(),
                efficiencyMode = credentialStore.isEfficiencyMode()
            )
        }
        refreshDiagEntries()
    }

    private fun observeInterfaces() {
        viewModelScope.launch {
            interfaceMonitor.state.collect { netState ->
                _uiState.update { it.copy(
                    interfaces = netState.allInterfaces.map { iface ->
                        InterfaceItem(iface.name, iface.type.name, iface.ipAddress, iface.ssid)
                    }
                )}
            }
        }
    }

    fun toggleDiagnostics(enabled: Boolean) {
        credentialStore.setDiagnosticMode(enabled)
        if (enabled) diagnostics.enable() else diagnostics.disable()
        _uiState.update { it.copy(diagnosticMode = enabled) }
        refreshDiagEntries()
    }

    fun setManagedMode(enabled: Boolean) {
        credentialStore.setManagedMode(enabled)
        _uiState.update { it.copy(managedMode = enabled) }
    }

    fun toggleEfficiencyMode(enabled: Boolean) {
        credentialStore.setEfficiencyMode(enabled)
        _uiState.update { it.copy(efficiencyMode = enabled) }
    }

    fun updateRouterHost(v: String) { _uiState.update { it.copy(routerHostInput = v) } }
    fun updateRouterUser(v: String) { _uiState.update { it.copy(routerUserInput = v) } }
    fun updateRouterPass(v: String) { _uiState.update { it.copy(routerPassInput = v) } }

    fun saveRouterConfig() {
        val s = _uiState.value
        if (s.routerHostInput.isBlank()) return
        credentialStore.saveRouterCredentials(s.routerHostInput, s.routerUserInput, s.routerPassInput)
        _uiState.update { it.copy(routerConfigured = true, routerHost = s.routerHostInput) }
    }

    fun exportDevicesCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            val devices = repository.allDevices.first()
            val result = dataExporter.exportDevicesCsv(devices)
            _uiState.update { it.copy(exportResult = formatExportResult(result)) }
        }
    }

    fun exportDevicesJson() {
        viewModelScope.launch(Dispatchers.IO) {
            val devices = repository.allDevices.first()
            val result = dataExporter.exportDevicesJson(devices)
            _uiState.update { it.copy(exportResult = formatExportResult(result)) }
        }
    }

    fun exportTrafficCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            val traffic = repository.getRecentTrafficSnapshot()
            val result = dataExporter.exportTrafficCsv(traffic)
            _uiState.update { it.copy(exportResult = formatExportResult(result)) }
        }
    }

    fun exportHistoryCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            // Export all history
            val history = repository.getDeviceHistory("", 1000) // empty mac = all
            val result = dataExporter.exportHistoryCsv(history)
            _uiState.update { it.copy(exportResult = formatExportResult(result)) }
        }
    }

    fun exportForensicSnapshot(context: android.content.Context) {
        viewModelScope.launch {
            val uri = repository.exportSnapshot(context)
            if (uri != null) {
                _uiState.update { it.copy(exportResult = "✓ Forensic Snapshot Ready") }
                // Use intent to share
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Network Snapshot"))
            } else {
                _uiState.update { it.copy(exportResult = "✗ Snapshot Generation Failed") }
            }
        }
    }

    fun clearDiagnostics() {
        diagnostics.clear()
        refreshDiagEntries()
    }

    private fun refreshDiagEntries() {
        val entries = diagnostics.getEntries(100).map { e ->
            DiagEntryItem(e.category.name, fmt.format(java.util.Date(e.timestamp)), e.message, e.details)
        }
        _uiState.update { it.copy(diagEntries = entries) }
    }

    private fun formatExportResult(result: DataExporter.ExportResult): String = when (result) {
        is DataExporter.ExportResult.Success -> "✓ Exported ${result.recordCount} records to ${result.filePath.substringAfterLast("/")}"
        is DataExporter.ExportResult.Error -> "✗ ${result.message}"
    }
}
