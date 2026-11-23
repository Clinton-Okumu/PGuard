package com.example.pguard.engine.accountability

import android.content.Context
import android.util.Log
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.RecordEventRequest
import com.example.pguard.engine.accessibility.BlockingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports critical security, tamper, and SOS events to the user's linked accountability
 * partner without blocking foreground protection threads or spamming identical alerts.
 */
object SecurityAlertReporter {
    private const val TAG = "SecurityAlertReporter"
    private const val DEDUP_WINDOW_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastReportedByType = ConcurrentHashMap<String, Long>()

    fun reportIfPartnerLinked(
        context: Context,
        eventType: String,
        message: String
    ) {
        val appContext = context.applicationContext
        if (!BlockingPreferences.isPartnerLinkedCached) return

        val now = System.currentTimeMillis()
        val prev = lastReportedByType[eventType]
        if (prev != null && (now - prev) < DEDUP_WINDOW_MS) {
            return
        }
        lastReportedByType[eventType] = now

        scope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val api = NetworkClient.getAccountabilityApiService(appContext)
                    api.recordEvent(
                        RecordEventRequest(
                            eventType = eventType,
                            message = message
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Deferred security alert '$eventType' (offline or unauthorized): ${e.message}")
            }
        }
    }
}
