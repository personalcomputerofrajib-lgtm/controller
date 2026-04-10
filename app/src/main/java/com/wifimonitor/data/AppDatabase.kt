package com.wifimonitor.data

import androidx.room.*

class Converters {
    @TypeConverter fun fromDeviceStatus(v: DeviceStatus) = v.name
    @TypeConverter fun toDeviceStatus(v: String) = try { DeviceStatus.valueOf(v) } catch (_: Exception) { DeviceStatus.UNKNOWN }

    @TypeConverter fun fromDeviceType(v: DeviceType) = v.name
    @TypeConverter fun toDeviceType(v: String) = try { DeviceType.valueOf(v) } catch (_: Exception) { DeviceType.UNKNOWN }

    @TypeConverter fun fromDeviceGroup(v: DeviceGroup) = v.name
    @TypeConverter fun toDeviceGroup(v: String) = try { DeviceGroup.valueOf(v) } catch (_: Exception) { DeviceGroup.UNKNOWN }

    @TypeConverter fun fromActivityLevel(v: ActivityLevel) = v.name
    @TypeConverter fun toActivityLevel(v: String) = try { ActivityLevel.valueOf(v) } catch (_: Exception) { ActivityLevel.IDLE }

    @TypeConverter fun fromAlertType(v: AlertType) = v.name
    @TypeConverter fun toAlertType(v: String) = try { AlertType.valueOf(v) } catch (_: Exception) { AlertType.NEW_DEVICE }

    @TypeConverter fun fromTrustLevel(v: TrustLevel) = v.name
    @TypeConverter fun toTrustLevel(v: String) = try { TrustLevel.valueOf(v) } catch (_: Exception) { TrustLevel.UNKNOWN }

    @TypeConverter fun fromRuleType(v: RuleType) = v.name
    @TypeConverter fun toRuleType(v: String) = try { RuleType.valueOf(v) } catch (_: Exception) { RuleType.DEVICE_DISCONNECT }
}

@Database(
    entities = [NetworkDevice::class, AlertRecord::class, TrafficRecord::class, AlertRule::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun trafficDao(): TrafficDao
}
