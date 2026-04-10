package com.wifimonitor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficDao {

    @Query("SELECT * FROM traffic_records ORDER BY timestamp DESC LIMIT 200")
    fun getRecentTraffic(): Flow<List<TrafficRecord>>

    /** Non-reactive snapshot for background analysis */
    @Query("SELECT * FROM traffic_records ORDER BY timestamp DESC LIMIT 200")
    suspend fun getRecentTrafficSnapshot(): List<TrafficRecord>

    @Query("SELECT * FROM traffic_records WHERE deviceMac = :mac ORDER BY timestamp DESC LIMIT 100")
    fun getTrafficForDevice(mac: String): Flow<List<TrafficRecord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: TrafficRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<TrafficRecord>)

    @Query("DELETE FROM traffic_records WHERE timestamp < :olderThan")
    suspend fun deleteOldRecords(olderThan: Long)

    @Query("SELECT COUNT(*) FROM traffic_records WHERE timestamp > :since")
    fun getTrafficCountSince(since: Long): Flow<Int>

    @Transaction
    @Query("SELECT domain, COUNT(*) as count FROM traffic_records WHERE timestamp > :since GROUP BY domain ORDER BY count DESC LIMIT 20")
    fun getTopDomains(since: Long): Flow<List<DomainCount>>

    @Transaction
    @Query("SELECT domain, COUNT(*) as count FROM traffic_records WHERE deviceMac = :mac AND timestamp > :since GROUP BY domain ORDER BY count DESC LIMIT 10")
    fun getTopDomainsForDevice(mac: String, since: Long): Flow<List<DomainCount>>
}

data class DomainCount(
    val domain: String,
    val count: Int
)
