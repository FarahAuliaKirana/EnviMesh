package com.envimesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envimesh.app.data.NodeConfig
import com.envimesh.app.data.SensorNode
import com.envimesh.app.data.status
import com.envimesh.app.ui.UiState
import com.envimesh.app.ui.components.InfoRow
import com.envimesh.app.ui.components.MutedText
import com.envimesh.app.ui.components.ScreenHeader
import com.envimesh.app.ui.components.SectionCard
import com.envimesh.app.ui.components.StatusDot
import com.envimesh.app.ui.components.StatusLegend
import com.envimesh.app.ui.components.VSpace
import com.envimesh.app.ui.components.color
import com.envimesh.app.ui.components.label
import com.envimesh.app.ui.theme.StatusBelow
import com.envimesh.app.ui.theme.StatusError
import com.envimesh.app.ui.theme.StatusGood
import com.envimesh.app.ui.theme.StatusOffline

/** Node list (App Inventor: Home). */
@Composable
fun HomeScreen(
    state: UiState,
    onSetup: () -> Unit,
    onOpenNode: (String) -> Unit,
    onRefresh: () -> Unit = {},
) {
    val threshold = state.config.waterThresholdCm
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("EnviMesh")
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        MutedText("Bluetooth")
                        Text(
                            "Connected via ${state.connectedDevice ?: "-"}",
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = onSetup,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text("Setup", maxLines = 1, softWrap = false)
                    }
                }
            }
            VSpace(12)
            DataSourceBar(state, onRefresh)
            VSpace(16)
            Text("Nodes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            MutedText("Water level = depth below peat surface. Threshold: $threshold cm")
            if (!state.onlineMode && state.myLocation != null) {
                MutedText("Simulated nodes placed around your current location.")
            }
            VSpace(8)
            SectionCard {
                state.nodes.forEachIndexed { i, node ->
                    NodeRow(node, threshold) { onOpenNode(node.id) }
                    if (i < state.nodes.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            VSpace(16)
            StatusLegend()
            VSpace(24)
        }
    }
}

/** Shows where the data comes from: Supabase (online) or local simulation. */
@Composable
private fun DataSourceBar(state: UiState, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (dot, text) = when {
            !state.onlineMode -> StatusOffline to "Offline simulation (random data on this phone)"
            state.syncError != null -> StatusBelow to "Cannot reach server: ${state.syncError}"
            state.lastSync == null -> StatusError to "Connecting to Supabase..."
            else -> StatusGood to "Online · Supabase · updated ${state.lastSync}" +
                if (state.activeAlertCount > 0) " · ${state.activeAlertCount} active alert(s)" else ""
        }
        Box(
            Modifier
                .size(8.dp)
                .background(dot, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.onlineMode) {
            TextButton(onClick = onRefresh) { Text("Refresh", maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun NodeRow(node: SensorNode, thresholdCm: Int, onClick: () -> Unit) {
    val status = node.status(thresholdCm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(12.dp))
        Text(node.id, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(node.waterLevelCm?.let { "$it cm" } ?: "--", color = status.color())
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open ${node.id}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Latest readings of one node (App Inventor: Monitoring). */
@Composable
fun MonitoringScreen(
    state: UiState,
    nodeId: String,
    onBack: () -> Unit,
    onShowOnMap: (Double, Double) -> Unit,
) {
    val node = state.nodes.firstOrNull { it.id == nodeId }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Monitoring", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) content@{
            if (node == null) {
                MutedText("Node $nodeId not found.")
                return@content
            }
            val status = node.status(state.config.waterThresholdCm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Spacer(Modifier.width(8.dp))
                Text(status.label(), color = status.color(), fontWeight = FontWeight.Medium)
            }
            MutedText("Received through the mesh")
            VSpace(12)
            SectionCard {
                InfoRow("Node", node.id)
                InfoRow("Water level", node.waterLevelCm?.let { "$it cm below surface" } ?: "--")
                InfoRow("Soil moisture", node.soilMoisturePct?.let { "$it%" } ?: "--")
                InfoRow("Temperature", node.temperatureC?.let { "$it °C" } ?: "--")
                InfoRow("Humidity", node.humidityPct?.let { "$it%" } ?: "--")
                InfoRow("Battery", node.batteryPct?.let { "$it%" } ?: "--")
                InfoRow("Last update", node.lastUpdateMinAgo?.let { "$it min ago" } ?: "--")
                InfoRow("Next update", nextUpdateText(node, state.config.wakeIntervalMin), showDivider = false)
            }
            VSpace(20)
            Button(
                onClick = { onShowOnMap(node.latitude, node.longitude) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Show on map") }
            VSpace(24)
        }
    }
}

private fun nextUpdateText(node: SensorNode, intervalMin: Int): String {
    val ago = node.lastUpdateMinAgo ?: return "--"
    if (!node.online) return "Overdue"
    val left = intervalMin - (ago % intervalMin)
    return "in about $left min"
}

/** Install and configure a node (App Inventor: Setup, UC-06). */
@Composable
fun SetupScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (NodeConfig) -> Unit,
) {
    var nodeId by remember { mutableStateOf(state.config.nodeId) }
    var interval by remember { mutableStateOf(state.config.wakeIntervalMin.toString()) }
    var threshold by remember { mutableStateOf(state.config.waterThresholdCm.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Setup", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            MutedText("Applies to the connected node: ${state.connectedDevice ?: "-"}")
            VSpace(16)
            OutlinedTextField(
                value = nodeId,
                onValueChange = { nodeId = it; error = null },
                label = { Text("Node ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(12)
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter(Char::isDigit); error = null },
                label = { Text("Wake-up interval (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(12)
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it.filter(Char::isDigit); error = null },
                label = { Text("Water level threshold (cm below surface)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                VSpace(8)
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            VSpace(24)
            Button(
                onClick = {
                    val i = interval.toIntOrNull()
                    val t = threshold.toIntOrNull()
                    error = when {
                        nodeId.isBlank() -> "Enter a node ID."
                        i == null || i !in 1..1440 -> "Interval must be 1 to 1440 minutes."
                        t == null || t !in 1..300 -> "Threshold must be 1 to 300 cm."
                        else -> null
                    }
                    if (error == null && i != null && t != null) {
                        onSave(NodeConfig(nodeId.trim(), i, t))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("Save") }
            VSpace(8)
            MutedText("Saved in the app only for now. Sending it to the node over Bluetooth comes next.")
            VSpace(24)
        }
    }
}

