package com.example.testapplication

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Esp32Data(
    val dev: String,
    val temps: List<Double>,
    val faults: List<Boolean>
)

data class UdpMessage(
    val id: Long = System.nanoTime(),
    val senderIp: String,
    val senderPort: Int,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val rawJson: String,
    val parsedEsp32: Esp32Data? = parseEsp32Data(rawJson)
)

fun parseEsp32Data(rawJson: String): Esp32Data? {
    return try {
        val json = JSONObject(rawJson.trim())
        val dev = json.optString("dev", "Unknown Device")

        val tempsList = mutableListOf<Double>()
        val tempsArray = json.optJSONArray("temps")
        if (tempsArray != null) {
            for (i in 0 until tempsArray.length()) {
                tempsList.add(tempsArray.getDouble(i))
            }
        }

        val faultsList = mutableListOf<Boolean>()
        val faultsArray = json.optJSONArray("faults")
        if (faultsArray != null) {
            for (i in 0 until faultsArray.length()) {
                faultsList.add(faultsArray.getBoolean(i))
            }
        }

        if (tempsList.isEmpty() && faultsList.isEmpty() && !json.has("dev")) {
            null
        } else {
            Esp32Data(
                dev = dev,
                temps = tempsList,
                faults = faultsList
            )
        }
    } catch (e: Exception) {
        null
    }
}
