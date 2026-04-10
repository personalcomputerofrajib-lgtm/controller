package com.wifimonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimonitor.data.ChangeEvent
import com.wifimonitor.data.ChangeLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val events: List<ChangeEvent> = emptyList()
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val changeLog: ChangeLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Ideally ChangeLogRepository would expose a Flow
            _uiState.update { it.copy(events = changeLog.getEvents()) }
        }
    }
    
    fun clear() {
        changeLog.clear()
        refresh()
    }
}
