package com.example.app_10.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class Esp32UdpReceiver(
    private val context: Context,
    private val port: Int = 5005
) {
    private val TAG = "Esp32UdpReceiver"

    /**
     * Listens continuously for UDP broadcast JSON packets on the Wi-Fi network.
     * Yields Map<Int, Float> mapping Sensor ID (1..6) -> Temperature value in °C.
     */
    fun listenForSensorData(): Flow<Map<Int, Float>> = flow {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            // Acquire Wi-Fi Multicast lock to ensure broadcast UDP packets are not dropped by Android OS
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("Esp32UdpMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            Log.d(TAG, "Listening for ESP32 UDP Broadcast packets on port $port")

            val buffer = ByteArray(2048)

            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val jsonString = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                Log.d(TAG, "Received packet from ${packet.address.hostAddress}: $jsonString")

                val parsedSensors = parseEsp32Json(jsonString)
                if (parsedSensors.isNotEmpty()) {
                    emit(parsedSensors)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP Socket error: ${e.localizedMessage}", e)
        } finally {
            try {
                multicastLock?.let {
                    if (it.isHeld) it.release()
                }
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket/lock: ${e.localizedMessage}")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Parses incoming JSON packet from ESP32-C3.
     * Supports flexible JSON formats:
     * - {"pt100_1": 24.5, "pt100_2": 31.0, "pt100_3": 45.2}
     * - {"device": "esp32_1", "sensors": [24.5, 31.0, 45.2]}
     * - {"sensor_id": 1, "temperature": 24.5}
     */
    private fun parseEsp32Json(jsonString: String): Map<Int, Float> {
        val sensorMap = mutableMapOf<Int, Float>()
        try {
            val json = JSONObject(jsonString)

            // Format 1: Direct key-value like {"pt100_1": 24.5, "pt100_2": 31.0, ...} or {"sensor1": 24.5}
            for (i in 1..6) {
                val keysToTry = listOf("pt100_$i", "sensor_$i", "sensor$i", "temp_$i", "s$i", "$i")
                for (key in keysToTry) {
                    if (json.has(key)) {
                        sensorMap[i] = json.getDouble(key).toFloat()
                        break
                    }
                }
            }

            // Format 2: Array format like {"sensors": [24.5, 31.0, 45.2, ...]}
            if (sensorMap.isEmpty() && json.has("sensors")) {
                val array = json.getJSONArray("sensors")
                val startOffset = json.optInt("start_index", 1) // Handles ESP32 #1 (1..3) and ESP32 #2 (4..6)
                for (i in 0 until array.length()) {
                    val sensorId = startOffset + i
                    if (sensorId in 1..6) {
                        sensorMap[sensorId] = array.getDouble(i).toFloat()
                    }
                }
            }

            // Format 3: Single sensor format like {"sensor_id": 1, "temperature": 24.5}
            if (sensorMap.isEmpty() && (json.has("sensor_id") || json.has("id"))) {
                val id = json.optInt("sensor_id", json.optInt("id", -1))
                val tempKey = if (json.has("temperature")) "temperature" else "temp"
                if (id in 1..6 && json.has(tempKey)) {
                    sensorMap[id] = json.getDouble(tempKey).toFloat()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON string: '$jsonString'. Error: ${e.message}")
        }
        return sensorMap
    }
}
