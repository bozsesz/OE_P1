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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val temperatures: List<Float> // Temp in °C
)

// Sample sensor readings over 7 time intervals
private val sampleSensors = listOf(
    Pt100SensorData(1, "PT100 #1", Color(0xFFE53935), listOf(22.5f, 35.0f, 48.2f, 62.0f, 75.5f, 88.0f, 94.2f)),
    Pt100SensorData(2, "PT100 #2", Color(0xFF1E88E5), listOf(18.0f, 24.5f, 31.0f, 40.2f, 49.8f, 58.4f, 65.0f)),
    Pt100SensorData(3, "PT100 #3", Color(0xFF43A047), listOf(30.0f, 32.5f, 35.0f, 38.0f, 42.0f, 45.5f, 48.0f)),
    Pt100SensorData(4, "PT100 #4", Color(0xFFFB8C00), listOf(15.0f, 28.0f, 45.0f, 65.0f, 82.0f, 98.5f, 112.0f)),
    Pt100SensorData(5, "PT100 #5", Color(0xFF8E24AA), listOf(25.0f, 27.0f, 29.5f, 31.0f, 33.2f, 36.0f, 38.5f)),
    Pt100SensorData(6, "PT100 #6", Color(0xFF00ACC1), listOf(40.0f, 42.0f, 48.0f, 56.5f, 68.0f, 80.2f, 91.0f))
)

private val timeLabels = listOf("0m", "5m", "10m", "15m", "20m", "25m", "30m")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    // Selected sensors state (by default all 6 sensors are selected)
    var selectedSensorIds by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PT100 Temperature Sensors Analytics",
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
                    .width(300.dp)
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
                        text = "Select sensors to plot on the graph:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Quick Select Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedSensorIds = setOf(1, 2, 3, 4, 5, 6) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("All 6", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedSensorIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("None", fontSize = 12.sp)
                        }
                    }

                    // Checkbox list for each sensor
                    sampleSensors.forEach { sensor ->
                        val isSelected = selectedSensorIds.contains(sensor.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedSensorIds = if (isSelected) {
                                        selectedSensorIds - sensor.id
                                    } else {
                                        selectedSensorIds + sensor.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedSensorIds = if (checked) {
                                        selectedSensorIds + sensor.id
                                    } else {
                                        selectedSensorIds - sensor.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = sensor.color)
                            )

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(sensor.color, shape = CircleShape)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = sensor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )

                            // Latest temperature reading
                            val lastTemp = sensor.temperatures.last()
                            Text(
                                text = "%.1f°C".format(lastTemp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) sensor.color else MaterialTheme.colorScheme.outline
                            )
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
                        sampleSensors.forEach { sensor ->
                            val isSelected = selectedSensorIds.contains(sensor.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSensorIds = if (isSelected) {
                                        selectedSensorIds - sensor.id
                                    } else {
                                        selectedSensorIds + sensor.id
                                    }
                                },
                                label = {
                                    Text("${sensor.name} (${sensor.temperatures.last()}°C)")
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(sensor.color, CircleShape)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sensor.color.copy(alpha = 0.15f),
                                    selectedLabelColor = sensor.color
                                )
                            )
                        }
                    }

                    // Canvas Graph
                    val activeSensors = sampleSensors.filter { selectedSensorIds.contains(it.id) }
                    Pt100GraphCanvas(
                        sensors = activeSensors,
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

    // Temperature bounds
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

            // Grid line
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            // Y-Label
            val labelText = "${tempValue.toInt()}°C"
            val textLayoutResult = textMeasurer.measure(labelText, labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(paddingLeft - textLayoutResult.size.width - 8.dp.toPx(), y - (textLayoutResult.size.height / 2))
            )
        }

        // 2. Draw Vertical Grid Lines & X-Axis Time Labels
        val totalPoints = timeLabels.size
        for (i in 0 until totalPoints) {
            val x = paddingLeft + (i.toFloat() / (totalPoints - 1) * graphWidth)

            // Grid line
            drawLine(
                color = gridColor,
                start = Offset(x, paddingTop),
                end = Offset(x, height - paddingBottom),
                strokeWidth = 1.dp.toPx()
            )

            // X-Label
            val timeText = timeLabels[i]
            val textLayoutResult = textMeasurer.measure(timeText, labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(x - (textLayoutResult.size.width / 2), height - paddingBottom + 6.dp.toPx())
            )
        }

        // 3. Draw Sensor Temperature Paths
        sensors.forEach { sensor ->
            if (sensor.temperatures.isNotEmpty()) {
                val path = Path()

                sensor.temperatures.forEachIndexed { index, temp ->
                    val x = paddingLeft + (index.toFloat() / (totalPoints - 1) * graphWidth)
                    val normalizedY = (temp - minTemp) / (maxTemp - minTemp)
                    val y = height - paddingBottom - (normalizedY * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = paddingLeft + ((index - 1).toFloat() / (totalPoints - 1) * graphWidth)
                        val prevTemp = sensor.temperatures[index - 1]
                        val prevNormalizedY = (prevTemp - minTemp) / (maxTemp - minTemp)
                        val prevY = height - paddingBottom - (prevNormalizedY * graphHeight)

                        // Cubic Bezier curve for smooth temperature trend lines
                        val controlX1 = prevX + (x - prevX) / 2f
                        val controlY1 = prevY
                        val controlX2 = prevX + (x - prevX) / 2f
                        val controlY2 = y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }

                    // Draw data point dots
                    drawCircle(
                        color = sensor.color,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // Draw sensor line
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
