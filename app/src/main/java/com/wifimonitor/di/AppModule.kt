package com.wifimonitor.di

import android.content.Context
import androidx.room.Room
import com.wifimonitor.analyzer.*
import com.wifimonitor.data.AppDatabase
import com.wifimonitor.data.DeviceDao
import com.wifimonitor.data.TrafficDao
import com.wifimonitor.network.SecureCredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wifi_monitor.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()
    @Provides @Singleton fun provideTrafficDao(db: AppDatabase): TrafficDao = db.trafficDao()

    // Analyzer modules
    @Provides @Singleton fun provideBehaviorEngine() = BehaviorEngine()
    @Provides @Singleton fun provideFaviconResolver() = FaviconResolver()
    @Provides @Singleton fun provideSignalProcessor() = SignalProcessor()
    @Provides @Singleton fun provideSessionTracker() = SessionTracker()
    @Provides @Singleton fun provideDeviceFingerprint() = DeviceFingerprint()
    @Provides @Singleton fun providePatternLearner() = PatternLearner()
    @Provides @Singleton fun provideDiagnosticLogger() = DiagnosticLogger()

    // Network modules
    @Provides @Singleton
    fun provideSecureCredentialStore(@ApplicationContext context: Context) = SecureCredentialStore(context)

    @Provides @Singleton
    fun provideNetworkInterfaceMonitor(@ApplicationContext context: Context) = NetworkInterfaceMonitor(context)
}
