package com.wifimonitor.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Router integration layer — supports OpenWrt, DD-WRT, ASUS, and generic routers.
 * Capabilities: DHCP table, ARP, bandwidth stats, DNS logs, device blocking.
 */
@Singleton
class RouterApiClient @Inject constructor(
    private val credentialStore: SecureCredentialStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    sealed class RouterResult<T> {
        data class Success<T>(val data: T) : RouterResult<T>()
        data class Error<T>(val message: String) : RouterResult<T>()
    }

    data class RouterDevice(
        val mac: String,
        val ip: String,
        val hostname: String = "",
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val connected: Boolean = true
    )

    data class DnsLog(
        val domain: String,
        val clientIp: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    val isConfigured: Boolean get() = credentialStore.hasRouterCredentials()
    val isManagedMode: Boolean get() = credentialStore.isManagedMode()

    // ── Connection Test ──

    suspend fun testConnection(): RouterResult<String> = withContext(Dispatchers.IO) {
        try {
            val host = credentialStore.getRouterHost()
            val result = withTimeoutOrNull(5000) {
                val req = Request.Builder().url("http://$host/").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 401) "Connected to $host"
                    else "HTTP ${resp.code}"
                }
            } ?: return@withContext RouterResult.Error("Connection timeout")
            RouterResult.Success(result)
        } catch (e: Exception) {
            RouterResult.Error(e.message ?: "Connection failed")
        }
    }

    // ── DHCP / Client List ──

    suspend fun getConnectedDevices(): RouterResult<List<RouterDevice>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Router not configured")

        val type = credentialStore.getRouterType()
        try {
            when (type) {
                "openwrt" -> getOpenWrtClients()
                "ddwrt" -> getDdwrtClients()
                "asus" -> getAsusClients()
                else -> autoDetectAndFetch()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConnectedDevices: ${e.message}")
            RouterResult.Error(e.message ?: "Failed to fetch")
        }
    }

    private suspend fun getOpenWrtClients(): RouterResult<List<RouterDevice>> {
        val host = credentialStore.getRouterHost()
        val user = credentialStore.getRouterUsername()
        val pass = credentialStore.getRouterPassword()

        // Get auth token
        val tokenBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"call\",\"params\":[\"00000000000000000000000000000000\",\"session\",\"login\",{\"username\":\"$user\",\"password\":\"$pass\"}]}"
        val tokenReq = Request.Builder()
            .url("http://$host/ubus")
            .post(tokenBody.toRequestBody("application/json".toMediaType()))
            .build()

        val tokenResp = client.newCall(tokenReq).execute()
        val tokenJson = tokenResp.body?.string() ?: return RouterResult.Error("No response")

        val tokenMatch = Regex("\"ubus_rpc_session\":\"([a-f0-9]+)\"").find(tokenJson)
            ?: return RouterResult.Error("Auth failed")
        val token = tokenMatch.groupValues[1]

        // Get DHCP leases
        val leaseBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"call\",\"params\":[\"$token\",\"luci-rpc\",\"getDHCPLeases\",{}]}"
        val leaseReq = Request.Builder()
            .url("http://$host/ubus")
            .post(leaseBody.toRequestBody("application/json".toMediaType()))
            .build()

        val leaseResp = client.newCall(leaseReq).execute()
        val leaseJson = leaseResp.body?.string() ?: return RouterResult.Error("No data")

        val devices = parseDhcpLeases(leaseJson)
        return RouterResult.Success(devices)
    }

    private suspend fun getDdwrtClients(): RouterResult<List<RouterDevice>> {
        val host = credentialStore.getRouterHost()
        val user = credentialStore.getRouterUsername()
        val pass = credentialStore.getRouterPassword()

        val credential = Credentials.basic(user, pass)
        val req = Request.Builder()
            .url("http://$host/Status_Lan.live.asp")
            .header("Authorization", credential)
            .build()

        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return RouterResult.Error("No response")

        val devices = parseDdwrtClients(body)
        return RouterResult.Success(devices)
    }

    private suspend fun getAsusClients(): RouterResult<List<RouterDevice>> {
        val host = credentialStore.getRouterHost()
        val user = credentialStore.getRouterUsername()
        val pass = credentialStore.getRouterPassword()

        // ASUS uses base64 auth token
        val authToken = android.util.Base64.encodeToString(
            "$user:$pass".toByteArray(), android.util.Base64.NO_WRAP
        )

        // Login
        val loginReq = Request.Builder()
            .url("http://$host/login.cgi")
            .post("login_authorization=$authToken".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val loginResp = client.newCall(loginReq).execute()
        val cookies = loginResp.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }

        // Get client list
        val clientReq = Request.Builder()
            .url("http://$host/appGet.cgi?hook=get_clientlist()")
            .header("Cookie", cookies)
            .build()

        val clientResp = client.newCall(clientReq).execute()
        val body = clientResp.body?.string() ?: return RouterResult.Error("No data")

        val devices = parseAsusClients(body)
        return RouterResult.Success(devices)
    }

    private suspend fun autoDetectAndFetch(): RouterResult<List<RouterDevice>> {
        // Try each method, first success wins
        for (method in listOf("openwrt", "asus", "ddwrt")) {
            try {
                val result = when (method) {
                    "openwrt" -> getOpenWrtClients()
                    "asus" -> getAsusClients()
                    "ddwrt" -> getDdwrtClients()
                    else -> continue
                }
                if (result is RouterResult.Success) {
                    credentialStore.saveRouterType(method)
                    return result
                }
            } catch (_: Exception) { continue }
        }
        return RouterResult.Error("Could not auto-detect router type")
    }

    // ── DNS Logs ──

    suspend fun fetchDnsLogs(): RouterResult<List<DnsLog>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        val type = credentialStore.getRouterType()
        try {
            when (type) {
                "openwrt" -> {
                    val cmd = "logread | grep 'query\\[A\\]'"
                    val output = executeOpenWrtCommand(cmd)
                    val logs = parseOpenWrtDnsLogs(output)
                    RouterResult.Success(logs)
                }
                else -> RouterResult.Error("DNS logs not supported for $type")
            }
        } catch (e: Exception) {
            RouterResult.Error("DNS Logs: ${e.message}")
        }
    }

    // ── Device Control ──

    suspend fun blockDevice(mac: String): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Router not configured")
        try {
            val host = credentialStore.getRouterHost()
            val type = credentialStore.getRouterType()

            when (type) {
                "openwrt" -> {
                    executeOpenWrtCommand("iptables -I FORWARD -m mac --mac-source $mac -j DROP")
                    RouterResult.Success(true)
                }
                "asus" -> {
                    val cookies = asusLogin()
                    val req = Request.Builder()
                        .url("http://$host/start_apply.htm")
                        .header("Cookie", cookies)
                        .post("action_mode=apply&MULTIFILTER_ENABLE=1&MULTIFILTER_MAC=$mac&MULTIFILTER_ENABLE_TIME=<&MULTIFILTER_DEVICENAME=blocked"
                            .toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                        .build()
                    client.newCall(req).execute()
                    RouterResult.Success(true)
                }
                else -> RouterResult.Error("Block not supported for $type")
            }
        } catch (e: Exception) {
            RouterResult.Error("Block failed: ${e.message}")
        }
    }

    suspend fun unblockDevice(mac: String): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Router not configured")
        try {
            val type = credentialStore.getRouterType()
            when (type) {
                "openwrt" -> {
                    executeOpenWrtCommand("iptables -D FORWARD -m mac --mac-source $mac -j DROP")
                    RouterResult.Success(true)
                }
                else -> RouterResult.Error("Unblock not supported for $type")
            }
        } catch (e: Exception) {
            RouterResult.Error("Unblock failed: ${e.message}")
        }
    }

    suspend fun setDeviceLimit(mac: String, kbps: Int): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        try {
            val type = credentialStore.getRouterType()
            if (type == "openwrt") {
                val iface = "br-lan"
                // This is a simplified TC implementation for demonstration
                executeOpenWrtCommand("tc qdisc del dev $iface root handle 1: htb default 10")
                executeOpenWrtCommand("tc qdisc add dev $iface root handle 1: htb default 10")
                executeOpenWrtCommand("tc class add dev $iface parent 1: classid 1:1 htb rate ${kbps}kbit")
                RouterResult.Success(true)
            } else {
                RouterResult.Error("Limit not supported for $type")
            }
        } catch (e: Exception) {
            RouterResult.Error("Limit failed: ${e.message}")
        }
    }

    // ── Infrastructure Redirection (Level 4 Authority) ──

    suspend fun enableTransparentDnsRedirection(phoneIp: String, phonePort: Int = 8053): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        try {
            if (credentialStore.getRouterType() == "openwrt") {
                // Redirect network UDP 53 to our local DNS server
                executeOpenWrtCommand("iptables -t nat -I PREROUTING -p udp --dport 53 -j DNAT --to $phoneIp:$phonePort")
                RouterResult.Success(true)
            } else {
                RouterResult.Error("Redirect not supported for this router")
            }
        } catch (e: Exception) {
            RouterResult.Error("DNS Redirect failed: ${e.message}")
        }
    }

    suspend fun disableTransparentDnsRedirection(): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        try {
            if (credentialStore.getRouterType() == "openwrt") {
                // Note: This is an aggressive flush for demonstration; in prod we'd track the specific rule index
                executeOpenWrtCommand("iptables -t nat -F PREROUTING")
                RouterResult.Success(true)
            } else {
                RouterResult.Error("Not supported")
            }
        } catch (e: Exception) {
            RouterResult.Error("Flush failed: ${e.message}")
        }
    }

    suspend fun enableTransparentProxyRedirection(phoneIp: String, phonePort: Int = 8080): RouterResult<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        try {
            if (credentialStore.getRouterType() == "openwrt") {
                // Redirect HTTP/HTTPS for DPI extraction
                executeOpenWrtCommand("iptables -t nat -I PREROUTING -p tcp --dport 80 -j DNAT --to $phoneIp:$phonePort")
                executeOpenWrtCommand("iptables -t nat -I PREROUTING -p tcp --dport 443 -j DNAT --to $phoneIp:$phonePort")
                RouterResult.Success(true)
            } else {
                RouterResult.Error("Not supported")
            }
        } catch (e: Exception) {
            RouterResult.Error("Proxy Redirect failed: ${e.message}")
        }
    }

    // ── Bandwidth Stats ──

    suspend fun getBandwidthStats(): RouterResult<Map<String, Pair<Long, Long>>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RouterResult.Error("Not configured")
        try {
            val type = credentialStore.getRouterType()
            when (type) {
                "openwrt" -> {
                    val cmd = "cat /proc/net/dev"
                    val output = executeOpenWrtCommand(cmd)
                    val stats = parseNetDevStats(output)
                    RouterResult.Success(stats)
                }
                else -> RouterResult.Error("Bandwidth stats not supported for $type")
            }
        } catch (e: Exception) {
            RouterResult.Error("Bandwidth: ${e.message}")
        }
    }

    // ── Helpers ──

    private fun parseDhcpLeases(json: String): List<RouterDevice> {
        val devices = mutableListOf<RouterDevice>()
        val macPattern = Regex("\"macaddr\":\"([^\"]+)\"")
        val ipPattern = Regex("\"ipaddr\":\"([^\"]+)\"")
        val hostPattern = Regex("\"hostname\":\"([^\"]+)\"")
        val macs = macPattern.findAll(json).map { it.groupValues[1] }.toList()
        val ips = ipPattern.findAll(json).map { it.groupValues[1] }.toList()
        val hosts = hostPattern.findAll(json).map { it.groupValues[1] }.toList()
        for (i in macs.indices) {
            devices.add(RouterDevice(
                mac = macs[i],
                ip = ips.getOrElse(i) { "" },
                hostname = hosts.getOrElse(i) { "" }
            ))
        }
        return devices
    }

    private fun parseDdwrtClients(body: String): List<RouterDevice> {
        val devices = mutableListOf<RouterDevice>()
        val pattern = Regex("'([^']*)', *'([^']*)', *'([^']*)'")
        pattern.findAll(body).forEach { match ->
            val (host, ip, mac) = match.destructured
            devices.add(RouterDevice(mac = mac, ip = ip, hostname = host))
        }
        return devices
    }

    private fun parseAsusClients(body: String): List<RouterDevice> {
        val devices = mutableListOf<RouterDevice>()
        val macPattern = Regex("\"([0-9A-Fa-f:]{17})\"\\s*:\\s*\\{")
        val ipPattern = Regex("\"ip\":\"([^\"]+)\"")
        val namePattern = Regex("\"name\":\"([^\"]+)\"")
        macPattern.findAll(body).forEach { macMatch ->
            val mac = macMatch.groupValues[1]
            val block = body.substring(macMatch.range.last, minOf(macMatch.range.last + 500, body.length))
            val ip = ipPattern.find(block)?.groupValues?.get(1) ?: ""
            val name = namePattern.find(block)?.groupValues?.get(1) ?: ""
            devices.add(RouterDevice(mac = mac, ip = ip, hostname = name))
        }
        return devices
    }

    private fun parseOpenWrtDnsLogs(output: String): List<DnsLog> {
        val logs = mutableListOf<DnsLog>()
        val pattern = Regex("query\\[A\\] ([^ ]+) from ([0-9.]+)")
        pattern.findAll(output).forEach { match ->
            val (domain, ip) = match.destructured
            logs.add(DnsLog(domain = domain, clientIp = ip))
        }
        return logs.asReversed().take(50)
    }

    private fun parseNetDevStats(output: String): Map<String, Pair<Long, Long>> {
        val stats = mutableMapOf<String, Pair<Long, Long>>()
        output.lines().drop(2).forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 10) {
                val iface = parts[0].removeSuffix(":")
                val rxBytes = parts[1].toLongOrNull() ?: 0
                val txBytes = parts[9].toLongOrNull() ?: 0
                stats[iface] = rxBytes to txBytes
            }
        }
        return stats
    }

    private suspend fun executeOpenWrtCommand(cmd: String): String {
        val host = credentialStore.getRouterHost()
        val user = credentialStore.getRouterUsername()
        val pass = credentialStore.getRouterPassword()
        val authBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"call\",\"params\":[\"00000000000000000000000000000000\",\"session\",\"login\",{\"username\":\"$user\",\"password\":\"$pass\"}]}"
        val authReq = Request.Builder().url("http://$host/ubus").post(authBody.toRequestBody("application/json".toMediaType())).build()
        val authResp = client.newCall(authReq).execute().body?.string() ?: ""
        val token = Regex("\"ubus_rpc_session\":\"([a-f0-9]+)\"").find(authResp)?.groupValues?.get(1) ?: return ""
        val execBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"call\",\"params\":[\"$token\",\"file\",\"exec\",{\"command\":\"/bin/sh\",\"params\":[\"-c\",\"$cmd\"]}]}"
        val execReq = Request.Builder().url("http://$host/ubus").post(execBody.toRequestBody("application/json".toMediaType())).build()
        return client.newCall(execReq).execute().body?.string() ?: ""
    }

    private suspend fun asusLogin(): String {
        val host = credentialStore.getRouterHost()
        val user = credentialStore.getRouterUsername()
        val pass = credentialStore.getRouterPassword()
        val authToken = android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
        val req = Request.Builder().url("http://$host/login.cgi").post("login_authorization=$authToken".toRequestBody("application/x-www-form-urlencoded".toMediaType())).build()
        val resp = client.newCall(req).execute()
        return resp.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
    }

    companion object { const val TAG = "RouterApi" }
}
