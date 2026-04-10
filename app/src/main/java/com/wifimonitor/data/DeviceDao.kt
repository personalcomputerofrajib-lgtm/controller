package com.wifimonitor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    // ── Devices ──

    @Query("SELECT * FROM devices WHERE networkProfileId = :profileId ORDER BY lastSeen DESC")
    fun getAllDevices(profileId: String): Flow<List<NetworkDevice>>

    @Query("SELECT * FROM devices WHERE status = 'ONLINE' AND networkProfileId = :profileId ORDER BY lastSeen DESC")
    fun getOnlineDevices(profileId: String): Flow<List<NetworkDevice>>

    @Query("SELECT * FROM devices WHERE status = 'ONLINE' AND networkProfileId = :profileId")
    suspend fun getOnlineDevicesSnapshot(profileId: String): List<NetworkDevice>


    @Query("SELECT * FROM devices WHERE mac = :mac LIMIT 1")
    suspend fun getDeviceByMac(mac: String): NetworkDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: NetworkDevice)

    @Update
    suspend fun updateDevice(device: NetworkDevice)

    /**
     * Audit 3: Partial Update (Fixes Dirty Writes)
     * Updates only scanner-sourced fields to prevent overwriting persistent counts.
     */
    @Query("""
        UPDATE devices 
        SET ip = :ip, hostname = :hostname, lastSeen = :lastSeen, 
            status = :status, pingResponseMs = :ping, jitterMs = :jitter, 
            reliabilityPct = :reli, behaviorLabel = :label
        WHERE mac = :mac
    """)
    suspend fun updateScannerMetrics(
        mac: String, ip: String, hostname: String, lastSeen: Long,
        status: DeviceStatus, ping: Int, jitter: Int, reli: Int, label: String
    )

    @Query("SELECT COUNT(*) FROM devices WHERE status = 'ONLINE' AND networkProfileId = :profileId")
    fun getOnlineCount(profileId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE networkProfileId = :profileId")
    fun getTotalCount(profileId: String): Flow<Int>

    @Query("DELETE FROM devices WHERE lastSeen < :olderThan AND isKnown = 0")
    suspend fun pruneStaleDevices(olderThan: Long)

    @Query("UPDATE devices SET totalDownloadBytes = totalDownloadBytes + :down, totalUploadBytes = totalUploadBytes + :up WHERE mac = :mac")
    suspend fun incrementBytes(mac: String, down: Long, up: Long)

    @Query("UPDATE devices SET totalDownloadBytes = totalDownloadBytes + :down, totalUploadBytes = totalUploadBytes + :up WHERE ip = :ip")
    suspend fun incrementBytesByIp(ip: String, down: Long, up: Long)

    // ── Alerts ──

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 100")
    fun getAlerts(): Flow<List<AlertRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertRecord)

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun getUnreadAlertCount(): Flow<Int>

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllAlertsRead()

    @Query("DELETE FROM alerts WHERE timestamp < :olderThan")
    suspend fun pruneOldAlerts(olderThan: Long)

    // ── Device History ──

    @Insert
    suspend fun insertHistory(history: DeviceHistory)

    @Insert
    suspend fun insertHistoryBatch(histories: List<DeviceHistory>)

    @Query("SELECT * FROM device_history WHERE mac = :mac ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getHistoryForDevice(mac: String, limit: Int = 100): List<DeviceHistory>

    @Query("SELECT * FROM device_history WHERE mac = :mac AND timestamp > :since ORDER BY timestamp ASC")
    fun getHistoryFlow(mac: String, since: Long): Flow<List<DeviceHistory>>

    @Query("DELETE FROM device_history WHERE timestamp < :olderThan")
    suspend fun pruneOldHistory(olderThan: Long)

    // ── Custom Rules ──

    @Query("SELECT * FROM alert_rules")
    fun getAllRules(): Flow<List<AlertRule>>

    @Query("SELECT * FROM alert_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<AlertRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AlertRule)

    @Delete
    suspend fun deleteRule(rule: AlertRule)

    @Query("UPDATE alert_rules SET lastTriggered = :time WHERE id = :id")
    suspend fun updateRuleTriggerTime(id: Int, time: Long)

    @Query("UPDATE devices SET lastSeenPorts = :ports WHERE mac = :mac")
    suspend fun updateLastSeenPorts(mac: String, ports: String)

    @Query("UPDATE devices SET usualActiveHours = :hours WHERE mac = :mac")
    suspend fun updateUsualActiveHours(mac: String, hours: String)
}
