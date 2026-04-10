package com.wifimonitor.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wifimonitor.analyzer.TrafficAuditEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Level 8: Final Power Layer - Traffic Capture Engine.
 * Provides root-free per-packet inspection using Android VpnService.
 */
@AndroidEntryPoint
class TrafficAuditVpnService : VpnService() {

    @Inject lateinit var trafficEngine: TrafficAuditEngine

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    companion object {
        const val TAG = "TrafficAuditVpn"
        const val ACTION_START = "com.wifimonitor.vpn.START"
        const val ACTION_STOP = "com.wifimonitor.vpn.STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setSession("WiFi Intelligence Sentinel")
                .setMtu(1500)
                .setBlocking(true)

            vpnInterface = builder.establish()
            Log.i(TAG, "VPN Interface established")
            
            startPacketLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
        }
    }

    private fun startPacketLoop() {
        captureJob?.cancel()
        captureJob = scope.launch {
            val pfd = vpnInterface ?: return@launch
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            
            val buffer = ByteBuffer.allocate(32768)
            
            while (isActive) {
                try {
                    val length = input.read(buffer.array())
                    if (length > 0) {
                        buffer.limit(length)
                        buffer.rewind()
                        
                        // Level 8: Lightweight Packet Processing
                        processPacket(buffer, length)
                        
                        // Loopback for local capture (or forward in real implementation)
                        output.write(buffer.array(), 0, length)
                        buffer.clear()
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Packet loop error", e)
                    break
                }
            }
        }
    }

    private fun processPacket(buffer: ByteBuffer, length: Int) {
        // IP Header (Version 4)
        if (length < 20) return
        val version = (buffer.get(0).toInt() shr 4) and 0x0F
        if (version != 4) return

        val protocol = buffer.get(9).toInt() and 0xFF
        
        // Extract Source and Destination IPs
        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        buffer.position(12)
        buffer.get(srcIp)
        buffer.get(dstIp)
        
        val srcAddr = InetAddress.getByAddress(srcIp).hostAddress
        val dstAddr = InetAddress.getByAddress(dstIp).hostAddress

        // Feed to Audit Engine
        trafficEngine.recordPacket(srcAddr, dstAddr, length, protocol)
    }

    private fun stopVpn() {
        captureJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
        Log.i(TAG, "VPN Stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        scope.cancel()
    }
}
