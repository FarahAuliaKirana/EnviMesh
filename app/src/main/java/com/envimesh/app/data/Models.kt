package com.envimesh.app.data

/** Status shown with a coloured dot on Home and Map. */
enum class NodeStatus { GOOD, BELOW_THRESHOLD, OFFLINE }

/**
 * One sensor node in the field.
 * [waterLevelCm] is the depth of the water table BELOW the peat surface,
 * so a bigger number means drier peat (more fire risk).
 */
data class SensorNode(
    val id: String,
    val waterLevelCm: Int?,
    val soilMoisturePct: Int?,
    val temperatureC: Double?,
    val humidityPct: Int?,
    val batteryPct: Int?,
    val lastUpdateMinAgo: Int?,
    val latitude: Double,
    val longitude: Double,
    val online: Boolean,
)

/** A Bluetooth LE device found during a scan. [rssiDbm] is negative, e.g. -58. */
data class ScannedDevice(val name: String, val rssiDbm: Int)

/** Settings written to a node during installation (UC-06). */
data class NodeConfig(
    val nodeId: String,
    val wakeIntervalMin: Int,
    val waterThresholdCm: Int,
)

/** Incident location sent by the operator through the mesh (UC-04). */
data class IncidentAlert(
    val nodeId: String,
    val latitude: Double,
    val longitude: Double,
    val waterLevelCm: Int,
    val receivedAt: String,
    val distanceKm: Double?,
    val confirmed: Boolean = false,
)

fun SensorNode.status(thresholdCm: Int): NodeStatus = when {
    !online || waterLevelCm == null -> NodeStatus.OFFLINE
    waterLevelCm >= thresholdCm -> NodeStatus.BELOW_THRESHOLD
    else -> NodeStatus.GOOD
}
