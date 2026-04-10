package com.wifimonitor.scanner

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class PortScanResult(
    val ip: String,
    val openPorts: List<Int>
)

@Singleton
class PortScanner @Inject constructor() {
    private val tag = "PortScanner"
    // Audit 18: Global Singleton Semaphore for the entire app network stack
    private val globalSemaphore = Semaphore(20)

    // Common ports to check for device identification
    private val commonPorts = listOf(
        21,    // FTP
        22,    // SSH
        23,    // Telnet
        25,    // SMTP
        53,    // DNS
        80,    // HTTP
        110,   // POP3
        139,   // NetBIOS
        143,   // IMAP
        443,   // HTTPS
        445,   // SMB
        548,   // AFP (Apple Filing)
        554,   // RTSP (cameras/streaming)
        631,   // IPP (printers)
        1883,  // MQTT (IoT)
        3000,  // Dev servers
        3389,  // RDP
        5000,  // UPnP / various
        5353,  // mDNS
        7000,  // AirPlay
        7100,  // AirPlay alt
        8080,  // HTTP alt
        8443,  // HTTPS alt
        8888,  // Jupyter / dev
        9100,  // Printer
        49152  // UPnP
    )

    suspend fun scanDevice(ip: String, timeoutMs: Int = 300): PortScanResult = coroutineScope {
        val openPorts = commonPorts.map { port ->
            async(Dispatchers.IO) {
                globalSemaphore.withPermit {
                    isPortOpen(ip, port, timeoutMs)
                }
            }
        }.mapIndexedNotNull { index, deferred ->
            if (deferred.await()) commonPorts[index] else null
        }

        if (openPorts.isNotEmpty()) {
            Log.i(tag, "$ip open ports: $openPorts")
        }
        return PortScanResult(ip, openPorts)
    }

    suspend fun quickScan(ip: String): PortScanResult {
        // Only scan most identifying ports quickly
        val quickPorts = listOf(22, 80, 443, 548, 554, 631, 7000, 9100)
        val openPorts = quickPorts.filter { port ->
            withContext(Dispatchers.IO) { isPortOpen(ip, port, 200) }
        }
        return PortScanResult(ip, openPorts)
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                // Audit 18: TCP Reset (RST) Hard-Closing
                // Prevents the socket from staying in TIME_WAIT for 60s
                socket.setSoLinger(true, 0)
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun inferDeviceTypeFromPorts(ports: List<Int>): String {
        return when {
            ports.contains(9100) || ports.contains(631) -> "Printer"
            ports.contains(554) -> "IP Camera / Streaming Device"
            ports.contains(7000) || ports.contains(7100) -> "Apple TV / AirPlay"
            ports.contains(548) -> "Mac / Apple Device"
            ports.contains(3389) -> "Windows PC (RDP)"
            ports.contains(22) -> "Linux / SSH Server"
            ports.contains(1883) -> "IoT Device (MQTT)"
            ports.contains(80) || ports.contains(443) -> "Web Server / Smart Device"
            else -> ""
        }
    }
}
