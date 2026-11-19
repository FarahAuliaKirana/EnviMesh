package com.envimesh.app.data

import com.envimesh.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads EnviMesh data from Supabase (the same data the simulator writes).
 *
 * Uses the PUBLISHABLE key only: the database rules allow it to read, never to change data.
 * URL and key come from local.properties (supabase.url, supabase.publishableKey),
 * so they are never committed to GitHub.
 */
object SupabaseApi {

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    /** True when local.properties has both values. */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** Latest reading and status of every node (database view node_latest). */
    suspend fun fetchNodes(): List<SensorNode> {
        val rows = getArray("node_latest?select=*&order=id")
        val now = OffsetDateTime.now()
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            val lastSeen = o.optTime("last_seen_at")
            val status = when (o.optString("status")) {
                "good" -> NodeStatus.GOOD
                "warning", "danger" -> NodeStatus.BELOW_THRESHOLD
                "sensor_error" -> NodeStatus.SENSOR_ERROR
                else -> NodeStatus.OFFLINE
            }
            SensorNode(
                id = o.getString("id"),
                waterLevelCm = o.optNumber("water_level_cm")?.let { Math.round(it).toInt() },
                soilMoisturePct = o.optNumber("soil_moisture_pct")?.let { Math.round(it).toInt() },
                temperatureC = o.optNumber("air_temp_c"),
                humidityPct = o.optNumber("air_humidity_pct")?.let { Math.round(it).toInt() },
                batteryPct = o.optNumber("battery_pct")?.toInt(),
                lastUpdateMinAgo = lastSeen?.let { Duration.between(it, now).toMinutes().toInt().coerceAtLeast(0) },
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                online = status != NodeStatus.OFFLINE,
                serverStatus = status,
            )
        }
    }

    /** Active alerts, most serious first, newest first. */
    suspend fun fetchActiveAlerts(): List<IncidentAlert> {
        val rows = getArray("alerts?select=*&status=eq.active&order=created_at.desc&limit=50")
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val alerts = (0 until rows.length()).mapNotNull { i ->
            val o = rows.getJSONObject(i)
            val lat = o.optNumber("latitude") ?: return@mapNotNull null
            val lon = o.optNumber("longitude") ?: return@mapNotNull null
            IncidentAlert(
                nodeId = o.getString("node_id"),
                latitude = lat,
                longitude = lon,
                waterLevelCm = o.optNumber("water_level_cm")?.let { Math.round(it).toInt() } ?: 0,
                receivedAt = o.optTime("created_at")
                    ?.atZoneSameInstant(ZoneId.systemDefault())?.format(fmt) ?: "-",
                distanceKm = null,
                level = o.optString("level", "warning"),
                message = o.optString("message").takeIf { it.isNotBlank() && it != "null" },
                remoteId = o.getLong("id"),
            )
        }
        val rank = mapOf("possible_fire" to 0, "danger" to 1, "warning" to 2, "sensor_error" to 3)
        return alerts.sortedBy { rank[it.level] ?: 9 }
    }

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        check(isConfigured) { "Supabase URL/key not set in local.properties" }
        val conn = (URL("$baseUrl/rest/v1/$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("apikey", apiKey)
            // Old-style JWT keys also go in Authorization; new sb_publishable_ keys must not.
            if (apiKey.startsWith("eyJ")) setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: ${body.take(200)}")
            JSONArray(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.optNumber(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun JSONObject.optTime(key: String): OffsetDateTime? =
        if (!has(key) || isNull(key)) null else runCatching { OffsetDateTime.parse(getString(key)) }.getOrNull()
}
