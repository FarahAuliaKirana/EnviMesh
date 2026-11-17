package com.envimesh.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.envimesh.app.data.FakeMeshRepository
import com.envimesh.app.data.IncidentAlert
import com.envimesh.app.data.MeshRepository
import com.envimesh.app.data.NodeConfig
import com.envimesh.app.data.ScannedDevice
import com.envimesh.app.data.SensorNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val scanning: Boolean = false,
    val scanned: List<ScannedDevice> = emptyList(),
    val scanMessage: String? = null,
    val connectedDevice: String? = null,
    val nodes: List<SensorNode> = emptyList(),
    val config: NodeConfig = NodeConfig(nodeId = "NODE-01", wakeIntervalMin = 30, waterThresholdCm = 40),
    val alert: IncidentAlert? = null,
    val mapFocus: Pair<Double, Double>? = null,
)

class EnviMeshViewModel(
    private val repository: MeshRepository = FakeMeshRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(nodes = repository.nodes(), alert = repository.latestAlert())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanMessage = null) }
            val found = repository.scan()
            _state.update {
                it.copy(scanning = false, scanned = found, scanMessage = "${found.size} devices found")
            }
        }
    }

    fun connect(device: ScannedDevice) {
        _state.update { it.copy(connectedDevice = device.name) }
    }

    fun disconnect() {
        _state.update { it.copy(connectedDevice = null, scanned = emptyList(), scanMessage = null) }
    }

    fun saveConfig(config: NodeConfig) {
        _state.update { it.copy(config = config) }
    }

    fun confirmAlert() {
        _state.update { s -> s.copy(alert = s.alert?.copy(confirmed = true)) }
    }

    fun focusMap(latitude: Double, longitude: Double) {
        _state.update { it.copy(mapFocus = latitude to longitude) }
    }
}
