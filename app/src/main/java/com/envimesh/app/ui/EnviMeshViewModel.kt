package com.envimesh.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.envimesh.app.data.FakeMeshRepository
import com.envimesh.app.data.IncidentAlert
import com.envimesh.app.data.MeshRepository
import com.envimesh.app.data.NodeConfig
import com.envimesh.app.data.ScannedDevice
import com.envimesh.app.data.SensorNode
import com.envimesh.app.data.Simulation
import com.envimesh.app.data.SupabaseApi
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    /** The phone's real position, once GPS/network location is known. */
    val myLocation: Pair<Double, Double>? = null,
    /** true = data from Supabase (written by the simulator or gateway); false = random local data. */
    val onlineMode: Boolean = false,
    /** Supabase URL and key are present in local.properties. */
    val onlineAvailable: Boolean = false,
    /** Time of the last successful sync, e.g. "18:10:26". */
    val lastSync: String? = null,
    val syncError: String? = null,
    /** Number of active alerts on the server. */
    val activeAlertCount: Int = 0,
)

class EnviMeshViewModel(
    private val repository: MeshRepository = FakeMeshRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(
            nodes = repository.nodes(),
            alert = repository.latestAlert(),
            onlineAvailable = SupabaseApi.isConfigured,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** New alerts from the server, used for phone notifications. */
    private val _newAlerts = MutableSharedFlow<IncidentAlert>(extraBufferCapacity = 16)
    val newAlerts: SharedFlow<IncidentAlert> = _newAlerts.asSharedFlow()

    private var syncJob: Job? = null
    private val seenAlertIds = mutableSetOf<Long>()
    private var firstSync = true

    init {
        if (SupabaseApi.isConfigured) setOnlineMode(true)
    }

    // ---------------------------------------------------------------- Bluetooth (dummy for now)

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

    // ---------------------------------------------------------------- Online mode (Supabase)

    fun setOnlineMode(enabled: Boolean) {
        if (enabled && !SupabaseApi.isConfigured) return
        syncJob?.cancel()
        if (enabled) {
            firstSync = true
            seenAlertIds.clear()
            _state.update { it.copy(onlineMode = true, syncError = null, mapFocus = null) }
            syncJob = viewModelScope.launch {
                while (isActive) {
                    syncOnce()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        } else {
            // Back to local simulation (random nodes around the phone, if its location is known).
            val me = _state.value.myLocation
            _state.update {
                it.copy(
                    onlineMode = false, lastSync = null, syncError = null, activeAlertCount = 0,
                    nodes = repository.nodes(), alert = repository.latestAlert(), mapFocus = null,
                )
            }
            if (me != null) onMyLocation(me.first, me.second, reshuffle = true)
        }
    }

    /** "Refresh" button: fetch now instead of waiting for the next round. */
    fun syncNow() {
        if (_state.value.onlineMode) viewModelScope.launch { syncOnce() }
    }

    private suspend fun syncOnce() {
        try {
            val nodes = SupabaseApi.fetchNodes()
            val alerts = SupabaseApi.fetchActiveAlerts()
            val me = _state.value.myLocation
            val top = alerts.firstOrNull()?.let { a ->
                val km = me?.let { Simulation.distanceKm(it.first, it.second, a.latitude, a.longitude) }
                a.copy(distanceKm = km?.let { Math.round(it * 10) / 10.0 })
            }

            // Notify only about alerts that appeared after the app started syncing.
            val fresh = alerts.filter { it.remoteId != null && it.remoteId !in seenAlertIds }
            seenAlertIds += alerts.mapNotNull { it.remoteId }
            if (!firstSync) fresh.forEach { _newAlerts.tryEmit(it) }
            firstSync = false

            _state.update { s ->
                val keepConfirmed = s.alert?.remoteId != null && s.alert.remoteId == top?.remoteId && s.alert.confirmed
                s.copy(
                    nodes = nodes,
                    alert = top?.copy(confirmed = keepConfirmed),
                    activeAlertCount = alerts.size,
                    lastSync = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    syncError = null,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(syncError = e.message ?: e.javaClass.simpleName) }
        }
    }

    // ---------------------------------------------------------------- Phone location

    /**
     * Called when the phone's location is known.
     * Offline mode: the first time (or when [reshuffle] is true) random nodes are placed around it.
     * Online mode: only remembered (used for the distance to an alert); nodes come from the server.
     */
    fun onMyLocation(latitude: Double, longitude: Double, reshuffle: Boolean = false) {
        val current = _state.value
        if (current.onlineMode || (current.myLocation != null && !reshuffle)) {
            _state.update { it.copy(myLocation = latitude to longitude) }
            return
        }
        val nodes = Simulation.nodesAround(latitude, longitude)
        val driest = nodes
            .filter { it.online && (it.waterLevelCm ?: 0) >= current.config.waterThresholdCm }
            .maxByOrNull { it.waterLevelCm ?: 0 }
        val alert = driest?.let { n ->
            IncidentAlert(
                nodeId = n.id,
                latitude = n.latitude,
                longitude = n.longitude,
                waterLevelCm = n.waterLevelCm ?: 0,
                receivedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                distanceKm = Math.round(
                    Simulation.distanceKm(latitude, longitude, n.latitude, n.longitude) * 10
                ) / 10.0,
                level = "warning",
            )
        }
        _state.update {
            it.copy(
                myLocation = latitude to longitude,
                nodes = nodes,
                alert = alert ?: it.alert,
                mapFocus = latitude to longitude,
            )
        }
    }

    /** "Randomize nodes" button on the map (offline mode only). */
    fun reshuffleNodes() {
        val me = _state.value.myLocation ?: return
        if (_state.value.onlineMode) return
        onMyLocation(me.first, me.second, reshuffle = true)
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 10_000L
    }
}
