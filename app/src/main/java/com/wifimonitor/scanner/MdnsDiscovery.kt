package com.wifimonitor.scanner

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class MdnsDevice(
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String
)

@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private val tag = "MdnsDiscovery"
    private val serviceTypes = listOf(
        "_http._tcp.",
        "_https._tcp.",
        "_ssh._tcp.",
        "_smb._tcp.",
        "_ftp._tcp.",
        "_airplay._tcp.",
        "_googlecast._tcp.",
        "_device-info._tcp.",
        "_services._dns-sd._udp."
    )

    suspend fun discoverDevices(timeoutMs: Long = 5000L): List<MdnsDevice> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val multicastLock = wifiManager.createMulticastLock(tag)
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discovered = mutableListOf<MdnsDevice>()

        try {
            // Audit 10: Hold lock during discovery window
            multicastLock.acquire()
            for (serviceType in serviceTypes) {
                withTimeoutOrNull(timeoutMs / serviceTypes.size) {
                    discoverServiceType(nsdManager, serviceType, discovered)
                }
            }
        } finally {
            if (multicastLock.isHeld) multicastLock.release()
        }

        Log.i(tag, "mDNS discovery found ${discovered.size} services")
        return discovered
    }

    private suspend fun discoverServiceType(
        nsdManager: NsdManager,
        serviceType: String,
        results: MutableList<MdnsDevice>
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "Discovery failed for $serviceType: $errorCode")
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "Stop discovery failed for $serviceType: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(tag, "Discovery started: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                
                // ── Quality #17: Resolve with Timeout (Fixes listener leak) ──
                scope.launch {
                    withTimeoutOrNull(2000L) {
                        suspendCancellableCoroutine<Unit> { resolveCont ->
                            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                    if (resolveCont.isActive) resolveCont.resume(Unit)
                                }
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    results.add(MdnsDevice(
                                        name = info.serviceName,
                                        host = info.host?.hostAddress ?: "",
                                        port = info.port,
                                        serviceType = info.serviceType
                                    ))
                                    if (resolveCont.isActive) resolveCont.resume(Unit)
                                }
                            })
                        }
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation {
                try { nsdManager.stopServiceDiscovery(listener) } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start discovery for $serviceType: ${e.message}")
            if (cont.isActive) cont.resume(Unit)
        }
    }

    fun enrichDevicesWithMdns(
        devices: List<com.wifimonitor.data.NetworkDevice>,
        mdnsResults: List<MdnsDevice>
    ): List<com.wifimonitor.data.NetworkDevice> {
        return devices.map { device ->
            val match = mdnsResults.firstOrNull { it.host == device.ip }
            if (match != null && device.hostname.isBlank()) {
                device.copy(hostname = match.name)
            } else {
                device
            }
        }
    }
}
