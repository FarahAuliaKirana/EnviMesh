package com.envimesh.app.data

import kotlinx.coroutines.delay

/**
 * Where the app gets its data. The UI only talks to this interface, so the
 * dummy implementation below can later be swapped for a real one that talks
 * to the team mesh device over Bluetooth LE (see ble/MeshCoreProtocol.kt).
 */
interface MeshRepository {
    suspend fun scan(): List<ScannedDevice>
    fun nodes(): List<SensorNode>
    fun latestAlert(): IncidentAlert?
}

/** Dummy data, same idea as the hard-coded values in the App Inventor version. */
class FakeMeshRepository : MeshRepository {

    override suspend fun scan(): List<ScannedDevice> {
        delay(1200) // pretend to scan
        return listOf(
            ScannedDevice("EnviMesh-Node-01", -58),
            ScannedDevice("EnviMesh-Node-02", -71),
            ScannedDevice("EnviMesh-Gateway", -94),
        )
    }

    override fun nodes(): List<SensorNode> = listOf(
        SensorNode(
            id = "Node-01", waterLevelCm = 42, soilMoisturePct = 31,
            temperatureC = 33.5, humidityPct = 58, batteryPct = 81,
            lastUpdateMinAgo = 5, latitude = -2.2456, longitude = 114.1234, online = true,
        ),
        SensorNode(
            id = "Node-02", waterLevelCm = 20, soilMoisturePct = 40,
            temperatureC = 30.0, humidityPct = 76, batteryPct = 64,
            lastUpdateMinAgo = 12, latitude = -2.2381, longitude = 114.1352, online = true,
        ),
        SensorNode(
            id = "Node-03", waterLevelCm = null, soilMoisturePct = null,
            temperatureC = null, humidityPct = null, batteryPct = 12,
            lastUpdateMinAgo = 190, latitude = -2.2539, longitude = 114.1105, online = false,
        ),
    )

    override fun latestAlert(): IncidentAlert = IncidentAlert(
        nodeId = "Node-01",
        latitude = -2.2456,
        longitude = 114.1234,
        waterLevelCm = 42,
        receivedAt = "14:22",
        distanceKm = 1.8,
    )
}
