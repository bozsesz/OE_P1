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

data class SensorPacket(
    val temperatures: Map<Int, Float>,
    val faults: Map<Int, Boolean>
)

class Esp32UdpReceiver(
    private val context: Context,
    private val port: Int = 5005
) {
    private val TAG = "Esp32UdpReceiver"

    /**
     * Listens continuously for UDP broadcast JSON packets on the Wi-Fi network.
     * Yields SensorPacket containing temperatures and fault flags for PT100 sensors.
     */
    fun listenForSensorData(): Flow<SensorPacket> = flow {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null

        try {
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

                val sensorPacket = parseEsp32Json(jsonString)
                if (sensorPacket.temperatures.isNotEmpty()) {
                    emit(sensorPacket)
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
     * Native support for:
     * {
     *   "dev": "ESP32_C3_01",
     *   "temps": [24.50, 31.00, 45.20, 18.00],
     *   "faults": [false, false, true, false]
     * }
     */
    private fun parseEsp32Json(jsonString: String): SensorPacket {
        val tempMap = mutableMapOf<Int, Float>()
        val faultMap = mutableMapOf<Int, Boolean>()

        try {
            val json = JSONObject(jsonString)

            // Determine starting sensor ID based on "start_id" or "dev" name
            val devName = json.optString("dev", "ESP32_C3_01")
            var startOffset = json.optInt("start_id", json.optInt("start_index", -1))

            if (startOffset == -1) {
                startOffset = if (devName.contains("02") || devName.endsWith("2")) 5 else 1
            }

            // 1. Array Format with "temps" and "faults"
            if (json.has("temps")) {
                val tempsArray = json.getJSONArray("temps")
                val faultsArray = if (json.has("faults")) json.getJSONArray("faults") else null

                for (i in 0 until tempsArray.length()) {
                    val sensorId = startOffset + i
                    if (sensorId in 1..6) {
                        val temp = tempsArray.getDouble(i).toFloat()
                        val isFault = faultsArray?.optBoolean(i, false) ?: (temp < -40f || temp > 400f)

                        tempMap[sensorId] = temp
                        faultMap[sensorId] = isFault
                    }
                }
            }

            // 2. Fallback Key-Value Format like {"pt100_1": 24.5, "pt100_1_fault": false}
            if (tempMap.isEmpty()) {
                for (i in 1..6) {
                    val keysToTry = listOf("pt100_$i", "sensor_$i", "sensor$i", "temp_$i")
                    for (key in keysToTry) {
                        if (json.has(key)) {
                            val temp = json.getDouble(key).toFloat()
                            val faultKey = "${key}_fault"
                            val isFault = if (json.has(faultKey)) json.getBoolean(faultKey) else (temp < -40f || temp > 400f)

                            tempMap[i] = temp
                            faultMap[i] = isFault
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON string: '$jsonString'. Error: ${e.message}")
        }

        return SensorPacket(tempMap, faultMap)
    }
}
