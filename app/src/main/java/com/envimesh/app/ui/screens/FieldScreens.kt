package com.envimesh.app.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.envimesh.app.R
import com.envimesh.app.data.status
import com.envimesh.app.ui.UiState
import com.envimesh.app.ui.components.InfoRow
import com.envimesh.app.ui.components.MutedText
import com.envimesh.app.ui.components.ScreenHeader
import com.envimesh.app.ui.components.SectionCard
import com.envimesh.app.ui.components.StatusLegend
import com.envimesh.app.ui.components.VSpace
import com.envimesh.app.ui.components.color
import com.envimesh.app.ui.components.label
import com.envimesh.app.ui.theme.EnviAccent
import com.envimesh.app.ui.theme.StatusBelow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Node and incident positions (App Inventor: Map), centred on the phone's real location. */
@Composable
fun MapScreen(
    state: UiState,
    onMyLocation: (Double, Double) -> Unit = { _, _ -> },
    onReshuffle: () -> Unit = {},
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    var hasLocationPermission by remember { mutableStateOf(inPreview || isLocationGranted(context)) }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        asked = true
        hasLocationPermission = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Map")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = when {
                state.myLocation != null -> "Simulated nodes around your location"
                !hasLocationPermission && asked -> "Location permission denied, showing sample area"
                else -> "Finding your location..."
            }
            MutedText(status, modifier = Modifier.weight(1f))
            if (state.myLocation != null) {
                TextButton(onClick = onReshuffle) { Text("Randomize", maxLines = 1, softWrap = false) }
            }
        }
        VSpace(4)
        val mapModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        if (inPreview) {
            Box(
                mapModifier.background(Color(0xFF2A3B33)),
                contentAlignment = Alignment.Center,
            ) { MutedText("Map preview (runs on device/emulator)") }
        } else {
            OsmMap(state, hasLocationPermission, onMyLocation, mapModifier)
        }
        StatusLegend(Modifier.padding(vertical = 12.dp))
    }
}

private fun isLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Pair<Double, Double>? = try {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { it.latitude to it.longitude }
} catch (e: SecurityException) {
    null
}

@Composable
private fun OsmMap(
    state: UiState,
    hasLocationPermission: Boolean,
    onMyLocation: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }
    val lastCenter = remember { arrayOfNulls<Pair<Double, Double>>(1) }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose { }
        lastKnownLocation(context)?.let { (lat, lon) -> onMyLocation(lat, lon) }
        val provider = GpsMyLocationProvider(context).apply {
            addLocationSource(LocationManager.NETWORK_PROVIDER)
        }
        val overlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            runOnFirstFix {
                val p = myLocation ?: return@runOnFirstFix
                mapView.post { onMyLocation(p.latitude, p.longitude) }
            }
        }
        mapView.overlays.add(overlay)
        onDispose {
            overlay.disableMyLocation()
            mapView.overlays.remove(overlay)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }
            val threshold = state.config.waterThresholdCm
            state.nodes.forEach { node ->
                val st = node.status(threshold)
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(node.latitude, node.longitude)
                    title = node.id
                    snippet = st.label() + (node.waterLevelCm?.let { " · $it cm" } ?: "")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setIcon(dot(context, st.color()))
                })
            }
            state.alert?.let { a ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(a.latitude, a.longitude)
                    title = "Incident at ${a.nodeId}"
                    snippet = "Received ${a.receivedAt}"
                })
            }
            val center = state.mapFocus
                ?: state.myLocation
                ?: state.nodes.firstOrNull()?.let { it.latitude to it.longitude }
            if (center != null && center != lastCenter[0]) {
                map.controller.setCenter(GeoPoint(center.first, center.second))
                lastCenter[0] = center
            }
            map.invalidate()
        },
    )
}

private fun dot(context: Context, color: Color): Drawable? =
    ContextCompat.getDrawable(context, R.drawable.marker_dot)?.mutate()?.apply { setTint(color.toArgb()) }

/** Incident location received through the mesh (App Inventor: Alert, UC-04). */
@Composable
fun AlertScreen(
    state: UiState,
    onNavigate: (Double, Double) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Alert")
        Column(Modifier.padding(horizontal = 20.dp)) content@{
            val alert = state.alert
            if (alert == null) {
                MutedText("No active alerts.")
                return@content
            }
            Text("Coordinates received", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = StatusBelow)
            MutedText("Sent by the operator through the mesh")
            VSpace(12)
            SectionCard {
                InfoRow("Node", alert.nodeId)
                InfoRow("Latitude", alert.latitude.toString())
                InfoRow("Longitude", alert.longitude.toString())
                InfoRow("Water level", "${alert.waterLevelCm} cm below surface")
                InfoRow("Received", alert.receivedAt)
                InfoRow("Distance", alert.distanceKm?.let { "$it km" } ?: "Needs phone GPS", showDivider = false)
            }
            VSpace(20)
            Button(
                onClick = { onNavigate(alert.latitude, alert.longitude) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Navigate") }
            VSpace(10)
            OutlinedButton(
                onClick = onConfirm,
                enabled = !alert.confirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text(if (alert.confirmed) "Confirmed" else "Confirm") }
            if (alert.confirmed) {
                VSpace(8)
                Text("Confirmation will be sent back through the mesh.", color = EnviAccent)
            }
            VSpace(24)
        }
    }
}

/** App and connection info (App Inventor: Settings). */
@Composable
fun SettingsScreen(state: UiState, onDisconnect: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings")
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionCard {
                InfoRow("Connected device", state.connectedDevice ?: "-")
                InfoRow("Language", "English")
                InfoRow("App version", "0.1 prototype (Kotlin)", showDivider = false)
            }
            VSpace(20)
            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Disconnect") }
        }
    }
}
