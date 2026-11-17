package com.envimesh.app.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object Simulation {

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    fun nodesAround(
        latitude: Double,
        longitude: Double,
        count: Int = 5,
        radiusMeters: Double = 1500.0,
        random: Random = Random.Default,
    ): List<SensorNode> = (1..count).map { i ->
        val distance = radiusMeters * sqrt(random.nextDouble())
        val bearing = random.nextDouble() * 2 * PI
        val dLat = distance * cos(bearing) / METERS_PER_DEGREE_LAT
        val dLon = distance * sin(bearing) / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude)))

        val online = i != count                 // node terakhir offline
        val water = when {
            !online -> null
            i == 1 -> random.nextInt(42, 61)     // Node-01 selalu di atas threshold
            else -> random.nextInt(10, 61)
        }
        SensorNode(
            id = "Node-%02d".format(i),
            waterLevelCm = water,
            soilMoisturePct = if (online) random.nextInt(20, 61) else null,
            temperatureC = if (online) (random.nextInt(270, 360) / 10.0) else null,
            humidityPct = if (online) random.nextInt(50, 96) else null,
            batteryPct = if (online) random.nextInt(35, 101) else random.nextInt(3, 15),
            lastUpdateMinAgo = if (online) random.nextInt(1, 29) else random.nextInt(120, 300),
            latitude = latitude + dLat,
            longitude = longitude + dLon,
            online = online,
        )
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}