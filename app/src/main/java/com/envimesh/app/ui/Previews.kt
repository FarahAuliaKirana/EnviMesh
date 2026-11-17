package com.envimesh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.envimesh.app.data.FakeMeshRepository
import com.envimesh.app.data.ScannedDevice
import com.envimesh.app.ui.screens.AlertScreen
import com.envimesh.app.ui.screens.HomeScreen
import com.envimesh.app.ui.screens.IntroScreen
import com.envimesh.app.ui.screens.MapScreen
import com.envimesh.app.ui.screens.MonitoringScreen
import com.envimesh.app.ui.screens.ScanScreen
import com.envimesh.app.ui.screens.SettingsScreen
import com.envimesh.app.ui.screens.SetupScreen
import com.envimesh.app.ui.theme.EnviBackground
import com.envimesh.app.ui.theme.EnviMeshTheme

/*
 * Android Studio previews of every screen, using the dummy data.
 * Open this file and switch to "Split" or "Design" (top-right of the editor).
 */

private val fake = FakeMeshRepository()

private val previewState = UiState(
    scanned = listOf(
        ScannedDevice("EnviMesh-Node-01", -58),
        ScannedDevice("EnviMesh-Node-02", -71),
        ScannedDevice("EnviMesh-Gateway", -94),
    ),
    scanMessage = "3 devices found",
    connectedDevice = "EnviMesh-Node-01",
    nodes = fake.nodes(),
    alert = fake.latestAlert(),
)

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    EnviMeshTheme {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) { content() }
    }
}

@Preview(name = "1 Intro", widthDp = 360, heightDp = 760)
@Composable
private fun IntroPreview() = PreviewFrame { IntroScreen(onFinished = {}) }

@Preview(name = "2 Scan", widthDp = 360, heightDp = 760)
@Composable
private fun ScanPreview() = PreviewFrame {
    ScanScreen(previewState, onScan = {}, onSelect = {}, onContinue = {})
}

@Preview(name = "3 Home", widthDp = 360, heightDp = 760)
@Composable
private fun HomePreview() = PreviewFrame {
    HomeScreen(previewState, onSetup = {}, onOpenNode = {})
}

@Preview(name = "4 Monitoring", widthDp = 360, heightDp = 760)
@Composable
private fun MonitoringPreview() = PreviewFrame {
    MonitoringScreen(previewState, nodeId = "Node-01", onBack = {}, onShowOnMap = { _, _ -> })
}

@Preview(name = "5 Setup", widthDp = 360, heightDp = 760)
@Composable
private fun SetupPreview() = PreviewFrame {
    SetupScreen(previewState, onBack = {}, onSave = {})
}

@Preview(name = "6 Map", widthDp = 360, heightDp = 760)
@Composable
private fun MapPreview() = PreviewFrame { MapScreen(previewState) }

@Preview(name = "7 Alert", widthDp = 360, heightDp = 760)
@Composable
private fun AlertPreview() = PreviewFrame {
    AlertScreen(previewState, onNavigate = { _, _ -> }, onConfirm = {})
}

@Preview(name = "8 Settings", widthDp = 360, heightDp = 760)
@Composable
private fun SettingsPreview() = PreviewFrame {
    SettingsScreen(previewState, onDisconnect = {})
}
