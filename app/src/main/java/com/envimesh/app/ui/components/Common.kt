package com.envimesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envimesh.app.data.NodeStatus
import com.envimesh.app.ui.theme.StatusBelow
import com.envimesh.app.ui.theme.StatusGood
import com.envimesh.app.ui.theme.StatusOffline

fun NodeStatus.color(): Color = when (this) {
    NodeStatus.GOOD -> StatusGood
    NodeStatus.BELOW_THRESHOLD -> StatusBelow
    NodeStatus.OFFLINE -> StatusOffline
}

fun NodeStatus.label(): String = when (this) {
    NodeStatus.GOOD -> "Good"
    NodeStatus.BELOW_THRESHOLD -> "Below threshold"
    NodeStatus.OFFLINE -> "Offline"
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusDot(status: NodeStatus, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .background(status.color(), CircleShape)
    )
}

@Composable
fun StatusLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(NodeStatus.BELOW_THRESHOLD, NodeStatus.GOOD, NodeStatus.OFFLINE).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(s, 10)
                Spacer(Modifier.width(6.dp))
                Text(s.label(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** One "label ... value" line, used where App Inventor had two side-by-side ListViews. */
@Composable
fun InfoRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
    if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))
