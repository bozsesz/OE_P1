package com.example.app_10.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_10.data.Esp32UdpReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val udpReceiver = Esp32UdpReceiver(application.applicationContext, port = 5005)

    // Current temperature readings for sensors 1..6
    private val _sensorReadings = MutableStateFlow<Map<Int, Float>>(
        mapOf(
            1 to 22.5f,
            2 to 18.0f,
            3 to 30.0f,
            4 to 15.0f,
            5 to 25.0f,
            6 to 40.0f
        )
    )
    val sensorReadings: StateFlow<Map<Int, Float>> = _sensorReadings.asStateFlow()

    // History readings for analytics graphs
    private val _sensorHistory = MutableStateFlow<Map<Int, List<Float>>>(
        mapOf(
            1 to listOf(22.5f, 35.0f, 48.2f, 62.0f, 75.5f, 88.0f, 94.2f),
            2 to listOf(18.0f, 24.5f, 31.0f, 40.2f, 49.8f, 58.4f, 65.0f),
            3 to listOf(30.0f, 32.5f, 35.0f, 38.0f, 42.0f, 45.5f, 48.0f),
            4 to listOf(15.0f, 28.0f, 45.0f, 65.0f, 82.0f, 98.5f, 112.0f),
            5 to listOf(25.0f, 27.0f, 29.5f, 31.0f, 33.2f, 36.0f, 38.5f),
            6 to listOf(40.0f, 42.0f, 48.0f, 56.5f, 68.0f, 80.2f, 91.0f)
        )
    )
    val sensorHistory: StateFlow<Map<Int, List<Float>>> = _sensorHistory.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Listening on Wi-Fi (UDP Port 5005)")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    init {
        startListening()
    }

    private fun startListening() {
        viewModelScope.launch {
            udpReceiver.listenForSensorData().collect { incomingMap ->
                if (incomingMap.isNotEmpty()) {
                    _connectionStatus.value = "Active Wi-Fi Broadcast Received (${incomingMap.size} Sensors)"

                    // Update current readings
                    val updatedReadings = _sensorReadings.value.toMutableMap().apply {
                        putAll(incomingMap)
                    }
                    _sensorReadings.value = updatedReadings

                    // Update history for analytics (keep max 10 points per sensor)
                    val updatedHistory = _sensorHistory.value.toMutableMap()
                    incomingMap.forEach { (sensorId, temp) ->
                        val currentList = updatedHistory[sensorId] ?: emptyList()
                        val newList = (currentList + temp).takeLast(10)
                        updatedHistory[sensorId] = newList
                    }
                    _sensorHistory.value = updatedHistory
                }
            }
        }
    }
}
