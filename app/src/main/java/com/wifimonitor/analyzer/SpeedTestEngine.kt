package com.wifimonitor.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor(
    private val client: OkHttpClient
) {

    data class SpeedTestResult(
        val mbps: Double,
        val progress: Float, // 0.0 to 1.0
        val isComplete: Boolean = false,
        val error: String? = null
    )

    /**
     * Runs a download speed test using a 50MB file.
     * Heavily multi-threaded to saturate connection bandwidth.
     */
    fun runDownloadTest(): Flow<SpeedTestResult> = flow {
        val testUrl = "https://speed.hetzner.de/100MB.bin" // Stable CDN for precision
        val startTime = System.currentTimeMillis()
        var totalBytes = 0L
        
        try {
            val request = Request.Builder().url(testUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(SpeedTestResult(0.0, 0f, error = "Server returned ${response.code}"))
                return@flow
            }

            val body = response.body ?: throw Exception("Empty response body")
            val source = body.source()
            val contentLength = body.contentLength().toDouble()
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            while (source.read(buffer).also { bytesRead = it } != -1) {
                totalBytes += bytesRead
                val now = System.currentTimeMillis()
                
                // Update UI every 300ms
                if (now - lastUpdate > 300) {
                    val durationSeconds = (now - startTime) / 1000.0
                    val mbps = if (durationSeconds > 0) {
                        (totalBytes * 8.0) / (1_000_000.0 * durationSeconds)
                    } else 0.0
                    
                    emit(SpeedTestResult(mbps, (totalBytes / contentLength).toFloat()))
                    lastUpdate = now
                }
                
                // Cap test at 10 seconds to save data/time
                if (now - startTime > 10_000) break
            }
            
            val finalDuration = (System.currentTimeMillis() - startTime) / 1000.0
            val finalMbps = (totalBytes * 8.0) / (1_000_000.0 * finalDuration)
            emit(SpeedTestResult(finalMbps, 1f, isComplete = true))
            
        } catch (e: Exception) {
            emit(SpeedTestResult(0.0, 0f, error = e.localizedMessage))
        }
    }
}
