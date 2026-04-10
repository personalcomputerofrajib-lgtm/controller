package com.wifimonitor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.wifimonitor.analyzer.IntelligenceEngine
import com.wifimonitor.scanner.NetworkScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Precision-Scale: Nightly Deep Audit.
 * Runs at 3 AM to perform heavy OS discovery and catch high-noise anomalies
 * when the network is traditionally quiet.
 */
@HiltWorker
class NightModeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanner: NetworkScanner,
    private val intelligenceEngine: IntelligenceEngine
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Perform the heaviest scan type
            val result = scanner.fullScan()
            
            // Log the "Deep Night State" for fingerprinting
            // (Intelligence handling is implicitly done via updateScanResults in repo)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "NightDeepAudit"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(true) // Don't drain battery at night
                .build()

            // Calculate delay until 3 AM
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DATE, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<NightModeWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
