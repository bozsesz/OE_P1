package com.example.testapplication

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class UdpViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<UdpMessage>>(emptyList())
    val messages: StateFlow<List<UdpMessage>> = _messages.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _localIp = MutableStateFlow("0.0.0.0")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _receivedCount = MutableStateFlow(0)
    val receivedCount: StateFlow<Int> = _receivedCount.asStateFlow()

    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        refreshLocalIp()
        startListening()
    }

    fun refreshLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getLocalIpAddress()
            _localIp.value = ip
        }
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (listenJob?.isActive == true) return

        _errorMessage.value = null
        listenJob = viewModelScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                val wifiManager = getApplication<Application>()
                    .applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager

                multicastLock = wifiManager?.createMulticastLock("UdpJsonReceiverLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                socket = DatagramSocket(5005).apply {
                    reuseAddress = true
                    soTimeout = 2000
                }

                _isListening.value = true

                val buffer = ByteArray(2048)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val message = UdpMessage(
                            senderIp = packet.address?.hostAddress ?: "Unknown",
                            senderPort = packet.port,
                            rawJson = text
                        )

                        withContext(Dispatchers.Main) {
                            _messages.update { currentList ->
                                (listOf(message) + currentList).take(100)
                            }
                            _receivedCount.update { it + 1 }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Expected timeout to keep checking isActive for cancellation
                        continue
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "UDP Error on port 5005: ${e.localizedMessage}"
            } finally {
                socket?.close()
                try {
                    multicastLock?.let {
                        if (it.isHeld) it.release()
                    }
                } catch (_: Exception) {
                }
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _isListening.value = false
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _receivedCount.value = 0
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = getApplication<Application>()
                .applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager

            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unavailable"
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
