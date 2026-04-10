package com.wifimonitor.service

import android.app.ActivityManager
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Level 20: Self-Healing Watchdog.
 * Periodically checks if MonitorService is running and restarts it if lost.
 */
class ServiceWatchdogWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!isServiceRunning(MonitorService::class.java)) {
            android.util.Log.i("Watchdog", "Service not found. Triggering self-healing restart...")
            MonitorService.start(applicationContext)
        }
        return Result.success()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return try {
            manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val WORK_NAME = "monitor_watchdog_work"

        fun schedule(context: Context, intervalHours: Long = 4) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                intervalHours, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
