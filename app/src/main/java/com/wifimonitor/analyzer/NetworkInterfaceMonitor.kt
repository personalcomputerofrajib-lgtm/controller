package com.wifimonitor.analyzer

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-interface awareness — detects WiFi, Hotspot, Ethernet,
 * tracks interface changes and network transitions in real time.
 */
@Singleton
class NetworkInterfaceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class InterfaceInfo(
        val name: String,          // wlan0, eth0, etc.
        val type: InterfaceType,
        val ipAddress: String,
        val subnetMask: String,
        val gateway: String,
        val ssid: String = "",
        val isActive: Boolean = true
    )

    enum class InterfaceType { WIFI, ETHERNET, HOTSPOT, CELLULAR, UNKNOWN }

    data class NetworkState(
        val activeInterface: InterfaceInfo? = null,
        val allInterfaces: List<InterfaceInfo> = emptyList(),
        val isConnected: Boolean = false,
        val lastTransition: Long = 0L,
        val transitionCount: Int = 0
    )

    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        refreshInterfaces()
        registerCallback()
    }

    fun stopMonitoring() {
        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unregister callback: ${e.message}")
        }
    }

    fun refreshInterfaces() {
        val interfaces = mutableListOf<InterfaceInfo>()
        try {
            val netInterfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (ni in netInterfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress && it.address.size == 4 }
                if (addrs.isEmpty()) continue

                val ip = addrs.first().hostAddress ?: continue
                val type = inferInterfaceType(ni.name)
                val ssid = if (type == InterfaceType.WIFI) getWifiSsid() else ""

                interfaces.add(InterfaceInfo(
                    name = ni.name,
                    type = type,
                    ipAddress = ip,
                    subnetMask = computeSubnetMask(ni),
                    gateway = inferGateway(ip),
                    ssid = ssid
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshInterfaces: ${e.message}")
        }

        val activeWifi = interfaces.firstOrNull { it.type == InterfaceType.WIFI }
            ?: interfaces.firstOrNull()

        _state.value = _state.value.copy(
            activeInterface = activeWifi,
            allInterfaces = interfaces,
            isConnected = interfaces.isNotEmpty()
        )
    }

    private fun registerCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available")
                refreshInterfaces()
                _state.value = _state.value.copy(
                    lastTransition = System.currentTimeMillis(),
                    transitionCount = _state.value.transitionCount + 1
                )
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Network lost")
                refreshInterfaces()
                _state.value = _state.value.copy(
                    lastTransition = System.currentTimeMillis(),
                    transitionCount = _state.value.transitionCount + 1
                )
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                refreshInterfaces()
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "registerCallback: ${e.message}")
        }
    }

    private fun inferInterfaceType(name: String): InterfaceType = when {
        name.startsWith("wlan") -> InterfaceType.WIFI
        name.startsWith("eth") -> InterfaceType.ETHERNET
        name.startsWith("ap") || name.startsWith("swlan") -> InterfaceType.HOTSPOT
        name.startsWith("rmnet") || name.startsWith("ccmni") -> InterfaceType.CELLULAR
        else -> InterfaceType.UNKNOWN
    }

    private fun getWifiSsid(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
        } catch (e: Exception) { "" }
    }

    private fun computeSubnetMask(ni: NetworkInterface): String {
        return try {
            val prefixLen = ni.interfaceAddresses
                .firstOrNull { it.address.address.size == 4 }
                ?.networkPrefixLength?.toInt() ?: 24
            val mask = -1 shl (32 - prefixLen)
            "${(mask shr 24) and 0xFF}.${(mask shr 16) and 0xFF}.${(mask shr 8) and 0xFF}.${mask and 0xFF}"
        } catch (e: Exception) { "255.255.255.0" }
    }

    private fun inferGateway(ip: String): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gatewayInt = wm.dhcpInfo?.gateway ?: 0
            if (gatewayInt != 0) {
                // Endian-aware conversion (Audit 17)
                val b = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(gatewayInt).array()
                "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}"
            } else {
                ip.substringBeforeLast(".") + ".1"
            }
        } catch (e: Exception) {
            ip.substringBeforeLast(".") + ".1"
        }
    }

    companion object {
        const val TAG = "InterfaceMonitor"
    }
}
