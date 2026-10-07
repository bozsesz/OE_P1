package com.example.app_10.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_10.ui.theme.App_10Theme

data class Pt100SensorData(
    val id: Int,
    val name: String,
    val color: Color,
    val isFaulty: Boolean,
    val temperatures: List<Float>
)

private val sensorColors = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00ACC1)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    sensorHistory: Map<Int, List<Float>> = mapOf(
        1 to listOf(22.5f, 35.0f, 48.2f, 62.0f, 75.5f, 88.0f, 94.2f),
        2 to listOf(18.0f, 24.5f, 31.0f, 40.2f, 49.8f, 58.4f, 65.0f),
        3 to emptyList(), // Sensor #3 faulty
        4 to listOf(15.0f, 28.0f, 45.0f, 65.0f, 82.0f, 98.5f, 112.0f),
        5 to listOf(25.0f, 27.0f, 29.5f, 31.0f, 33.2f, 36.0f, 38.5f),
        6 to listOf(40.0f, 42.0f, 48.0f, 56.5f, 68.0f, 80.2f, 91.0f)
    ),
    faultySensors: Set<Int> = setOf(3)
) {
    // Initial selection excludes any faulty sensors
    val healthySensorIds = (1..6).filter { !faultySensors.contains(it) }.toSet()
    var selectedSensorIds by remember { mutableStateOf(healthySensorIds) }

    // Always ensure faulty sensors are excluded from active selection
    val activeSelection = selectedSensorIds.filter { !faultySensors.contains(it) }.toSet()

    val sensorsList = (1..6).map { id ->
        val isFaulty = faultySensors.contains(id)
        Pt100SensorData(
            id = id,
            name = "PT100 #$id",
            color = sensorColors[(id - 1) % sensorColors.size],
            isFaulty = isFaulty,
            temperatures = if (isFaulty) emptyList() else (sensorHistory[id] ?: listOf(0f))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PT100 Temperature Analytics (Wi-Fi Telemetry)",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Panel: Controls & Selection
            Card(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Sensor Selection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select healthy sensors to plot on graph:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Quick Select Buttons (only healthy sensors)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedSensorIds = healthySensorIds },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("All Healthy", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedSensorIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("None", fontSize = 11.sp)
                        }
                    }

                    // Checkbox list for each sensor
                    sensorsList.forEach { sensor ->
                        val isSelected = activeSelection.contains(sensor.id)
                        val isEnabled = !sensor.isFaulty

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = isEnabled) {
                                    selectedSensorIds = if (isSelected) {
                                        activeSelection - sensor.id
                                    } else {
                                        activeSelection + sensor.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected && isEnabled,
                                enabled = isEnabled,
                                onCheckedChange = { checked ->
                                    selectedSensorIds = if (checked) {
                                        activeSelection + sensor.id
                                    } else {
                                        activeSelection - sensor.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = sensor.color,
                                    disabledCheckedColor = Color.Gray
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(if (sensor.isFaulty) Color.Gray else sensor.color, shape = CircleShape)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = sensor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sensor.isFaulty) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (sensor.isFaulty) {
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "FAULTY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                val lastTemp = sensor.temperatures.lastOrNull() ?: 0f
                                Text(
                                    text = "%.1f°C".format(lastTemp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) sensor.color else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Right Panel: Graph & Active Legends
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Temperature Graph (°C)",
                        style = MaterialTheme.typography.titleLarge
                    )

                    // Active legend chips
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sensorsList.forEach { sensor ->
                            val isSelected = activeSelection.contains(sensor.id)
                            val isEnabled = !sensor.isFaulty
                            val lastTemp = sensor.temperatures.lastOrNull() ?: 0f

                            FilterChip(
                                selected = isSelected && isEnabled,
                                enabled = isEnabled,
                                onClick = {
                                    selectedSensorIds = if (isSelected) {
                                        activeSelection - sensor.id
                                    } else {
                                        activeSelection + sensor.id
                                    }
                                },
                                label = {
                                    if (sensor.isFaulty) {
                                        Text("${sensor.name} [FAULTY]")
                                    } else {
                                        Text("${sensor.name} (%.1f°C)".format(lastTemp))
                                    }
                                },
                                leadingIcon = {
                                    if (sensor.isFaulty) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Faulty",
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(sensor.color, CircleShape)
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sensor.color.copy(alpha = 0.15f),
                                    selectedLabelColor = sensor.color,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }

                    // Canvas Graph (plot only selected & non-faulty sensors)
                    val graphSensors = sensorsList.filter { activeSelection.contains(it.id) && !it.isFaulty }
                    Pt100GraphCanvas(
                        sensors = graphSensors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Pt100GraphCanvas(
    sensors: List<Pt100SensorData>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp
    )

    val minTemp = 0f
    val maxTemp = 120f
    val tempStep = 20f

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val paddingLeft = 50.dp.toPx()
        val paddingBottom = 30.dp.toPx()
        val paddingTop = 20.dp.toPx()
        val paddingRight = 20.dp.toPx()

        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // 1. Draw Horizontal Grid Lines & Y-Axis Labels
        val steps = ((maxTemp - minTemp) / tempStep).toInt()
        for (i in 0..steps) {
            val tempValue = minTemp + (i * tempStep)
            val y = height - paddingBottom - (i.toFloat() / steps * graphHeight)

            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            val labelText = "${tempValue.toInt()}°C"
            val textLayoutResult = textMeasurer.measure(labelText, labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(paddingLeft - textLayoutResult.size.width - 8.dp.toPx(), y - (textLayoutResult.size.height / 2))
            )
        }

        // 2. Draw Sensor Temperature Paths
        sensors.forEach { sensor ->
            val points = sensor.temperatures
            if (points.isNotEmpty()) {
                val path = Path()
                val totalPoints = points.size

                points.forEachIndexed { index, temp ->
                    val x = if (totalPoints > 1) {
                        paddingLeft + (index.toFloat() / (totalPoints - 1) * graphWidth)
                    } else {
                        paddingLeft + (graphWidth / 2f)
                    }
                    val normalizedY = (temp.coerceIn(minTemp, maxTemp) - minTemp) / (maxTemp - minTemp)
                    val y = height - paddingBottom - (normalizedY * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = paddingLeft + ((index - 1).toFloat() / (totalPoints - 1) * graphWidth)
                        val prevTemp = points[index - 1]
                        val prevNormalizedY = (prevTemp.coerceIn(minTemp, maxTemp) - minTemp) / (maxTemp - minTemp)
                        val prevY = height - paddingBottom - (prevNormalizedY * graphHeight)

                        val controlX1 = prevX + (x - prevX) / 2f
                        val controlY1 = prevY
                        val controlX2 = prevX + (x - prevX) / 2f
                        val controlY2 = y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }

                    drawCircle(
                        color = sensor.color,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                drawPath(
                    path = path,
                    color = sensor.color,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Preview(name = "Tablet View", device = Devices.TABLET, showBackground = true)
@Composable
fun AnalyticsPreview() {
    App_10Theme {
        AnalyticsScreen()
    }
}
