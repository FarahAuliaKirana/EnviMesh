package com.envimesh.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envimesh.app.data.ScannedDevice
import com.envimesh.app.ui.UiState
import com.envimesh.app.ui.components.MutedText
import com.envimesh.app.ui.components.ScreenHeader
import com.envimesh.app.ui.components.VSpace
import com.envimesh.app.ui.theme.EnviAccent
import com.envimesh.app.ui.theme.EnviGreen
import kotlinx.coroutines.delay

/** Splash screen (App Inventor: Intro). Moves on by itself after a short delay. */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(EnviGreen, CircleShape)
        )
        VSpace(20)
        Row {
            Text("Envi", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Mesh", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = EnviAccent)
        }
        VSpace(6)
        MutedText("Environmental data over LoRa mesh")
        VSpace(40)
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** Find and pick a node over Bluetooth (App Inventor: Screen1). */
@Composable
fun ScanScreen(
    state: UiState,
    onScan: () -> Unit,
    onSelect: (ScannedDevice) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Connect to a node")
        Column(Modifier.padding(horizontal = 20.dp)) {
            MutedText("Pair the node in Android Bluetooth settings first (PIN on the node or 123456), then scan here.")
            VSpace(16)
            Button(
                onClick = onScan,
                enabled = !state.scanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.size(10.dp))
                    Text("Scanning...")
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Scan for devices")
                }
            }
            VSpace(12)
            state.scanMessage?.let { MutedText(it) }
            state.connectedDevice?.let {
                VSpace(4)
                Text("Connected: $it", color = EnviAccent, fontWeight = FontWeight.Medium)
            }
            VSpace(8)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.scanned) { device ->
                DeviceRow(device, selected = device.name == state.connectedDevice) { onSelect(device) }
            }
        }
        if (state.connectedDevice != null) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, EnviAccent) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(device.name, fontWeight = FontWeight.Medium)
            Column(horizontalAlignment = Alignment.End) {
                Text("${device.rssiDbm} dBm")
                MutedText(signalQuality(device.rssiDbm))
            }
        }
    }
}

/** Rough Bluetooth LE signal quality from RSSI (always negative). */
private fun signalQuality(rssi: Int): String = when {
    rssi >= -70 -> "Strong"
    rssi >= -85 -> "Fair"
    else -> "Weak"
}
