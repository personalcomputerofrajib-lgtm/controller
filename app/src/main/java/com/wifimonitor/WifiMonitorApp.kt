package com.wifimonitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WifiMonitorApp : Application() {
    
    @javax.inject.Inject
    lateinit var diagnostics: com.wifimonitor.analyzer.DiagnosticLogger
    
    override fun onCreate() {
        super.onCreate()
        
        // Audit 19: Global Crash Recovery Handler
        // Ensures the monitor service survives transient process crashes
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            diagnostics.error("FATAL: App Crash", Exception(throwable))
            // Signal OS to restart service after delay
            oldHandler?.uncaughtException(thread, throwable)
        }

        // Level 20: Schedule background watchdog
        com.wifimonitor.service.ServiceWatchdogWorker.schedule(this)
    }
}
