package com.example.app_10.ui.dashboard

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.app_10.ui.theme.App_10Theme

private val sensorColors = listOf(
    Color(0xFFE53935), // PT100 #1 Red
    Color(0xFF1E88E5), // PT100 #2 Blue
    Color(0xFF43A047), // PT100 #3 Green
    Color(0xFFFB8C00), // PT100 #4 Orange
    Color(0xFF8E24AA), // PT100 #5 Purple
    Color(0xFF00ACC1)  // PT100 #6 Cyan
)

@Composable
fun DashBoardScreen(
    modifier: Modifier = Modifier,
    sensorReadings: Map<Int, Float> = mapOf(
        1 to 22.5f, 2 to 18.0f, 3 to -999.0f, 4 to 15.0f, 5 to 25.0f, 6 to 40.0f
    ),
    faultySensors: Set<Int> = setOf(3),
    onToggleFault: (Int) -> Unit = {},
    connectionStatus: String = "Listening on Wi-Fi (UDP Port 5005)"
) {
    val healthyCount = (1..6).count { !faultySensors.contains(it) }
    val faultyCount = (1..6).count { faultySensors.contains(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Network & Sensor Status Banner
        Surface(
            color = if (faultyCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (faultyCount > 0) Color(0xFFD32F2F) else Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Wi-Fi Telemetry: $connectionStatus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (faultyCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    color = if (faultyCount > 0) Color(0xFFD32F2F) else Color(0xFF388E3C),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (faultyCount > 0) "$healthyCount OK • $faultyCount FAULTY" else "6 / 6 ALL SENSORS OK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = "Live PT100 Sensors Grid",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(6) { index ->
                val sensorId = index + 1
                val sensorColor = sensorColors[index % sensorColors.size]
                val isFaulty = faultySensors.contains(sensorId)
                val currentTemp = sensorReadings[sensorId] ?: 0.0f

                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFaulty) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clickable { onToggleFault(sensorId) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(if (isFaulty) Color(0xFFD32F2F) else sensorColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PT100 #$sensorId",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Fault Indicator Badge
                            Surface(
                                color = if (isFaulty) Color(0xFFD32F2F) else Color(0xFF388E3C),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    if (isFaulty) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Faulty",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (isFaulty) "FAULTY" else "NORMAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Temperature or Fault Details
                        if (isFaulty) {
                            Column {
                                Text(
                                    text = "SENSOR FAULT",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Disconnected / Out of Range",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = "%.1f °C".format(currentTemp),
                                style = MaterialTheme.typography.headlineLarge,
                                color = sensorColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (isFaulty) "Tap card to simulate repair" else "Tap card to simulate fault",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Tablet View", device = Devices.TABLET, showBackground = true)
@Composable
fun DashBoardPreview() {
    App_10Theme {
        DashBoardScreen()
    }
}
