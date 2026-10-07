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
            3 to -999.0f, // PT100 #3 simulated open-circuit fault
            4 to 15.0f,
            5 to 25.0f,
            6 to 40.0f
        )
    )
    val sensorReadings: StateFlow<Map<Int, Float>> = _sensorReadings.asStateFlow()

    // Explicit set of faulty sensor IDs (e.g. PT100 #3 is faulty)
    private val _faultySensors = MutableStateFlow<Set<Int>>(setOf(3))
    val faultySensors: StateFlow<Set<Int>> = _faultySensors.asStateFlow()

    // History readings for analytics graphs
    private val _sensorHistory = MutableStateFlow<Map<Int, List<Float>>>(
        mapOf(
            1 to listOf(22.5f, 35.0f, 48.2f, 62.0f, 75.5f, 88.0f, 94.2f),
            2 to listOf(18.0f, 24.5f, 31.0f, 40.2f, 49.8f, 58.4f, 65.0f),
            3 to emptyList(), // Faulty sensor has no valid history
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

    /**
     * Checks if a sensor ID is faulty (either explicitly set as faulty or temperature reading out of range).
     */
    fun isSensorFaulty(sensorId: Int): Boolean {
        if (_faultySensors.value.contains(sensorId)) return true
        val temp = _sensorReadings.value[sensorId] ?: return true
        return isTemperatureOutofBounds(temp)
    }

    /**
     * Allows toggling fault state manually for testing/simulation purposes.
     */
    fun toggleSensorFault(sensorId: Int) {
        val currentFaults = _faultySensors.value.toMutableSet()
        if (currentFaults.contains(sensorId)) {
            currentFaults.remove(sensorId)
            // Restore default value if un-faulted
            if (_sensorReadings.value[sensorId] == -999.0f) {
                _sensorReadings.value = _sensorReadings.value.toMutableMap().apply { put(sensorId, 32.0f) }
            }
        } else {
            currentFaults.add(sensorId)
            _sensorReadings.value = _sensorReadings.value.toMutableMap().apply { put(sensorId, -999.0f) }
        }
        _faultySensors.value = currentFaults
    }

    private fun isTemperatureOutofBounds(temp: Float): Boolean {
        return temp < -40f || temp > 400f || temp.isNaN() || temp == -999.0f
    }

    private fun startListening() {
        viewModelScope.launch {
            udpReceiver.listenForSensorData().collect { incomingMap ->
                if (incomingMap.isNotEmpty()) {
                    _connectionStatus.value = "Active Wi-Fi Broadcast Received (${incomingMap.size} Sensors)"

                    val updatedReadings = _sensorReadings.value.toMutableMap()
                    val updatedFaults = _faultySensors.value.toMutableSet()

                    incomingMap.forEach { (sensorId, temp) ->
                        updatedReadings[sensorId] = temp
                        if (isTemperatureOutofBounds(temp)) {
                            updatedFaults.add(sensorId)
                        } else {
                            updatedFaults.remove(sensorId)
                        }
                    }

                    _sensorReadings.value = updatedReadings
                    _faultySensors.value = updatedFaults

                    // Update history for healthy sensors
                    val updatedHistory = _sensorHistory.value.toMutableMap()
                    incomingMap.forEach { (sensorId, temp) ->
                        if (!updatedFaults.contains(sensorId)) {
                            val currentList = updatedHistory[sensorId] ?: emptyList()
                            val newList = (currentList + temp).takeLast(10)
                            updatedHistory[sensorId] = newList
                        }
                    }
                    _sensorHistory.value = updatedHistory
                }
            }
        }
    }
}
