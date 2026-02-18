package com.imhere.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.imhere.app.runtime.ListeningStateStore
import com.imhere.app.service.FinderForegroundService
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.FinderForegroundServiceEntry
import com.imhere.platform.monitoring.ForegroundServiceCommand
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppBootstrap(
    private val context: Context? = null,
    private val foregroundServiceEntry: FinderForegroundServiceEntry = FinderForegroundServiceEntry(),
    private val runtimePolicyMonitor: RuntimePolicyMonitor,
    private val orchestrator: FinderOrchestrator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val startServiceAction: (() -> Unit)? = null,
    private val stopServiceAction: (() -> Unit)? = null
) {
    private var monitorJob: Job? = null
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                runtimePolicyMonitor.updateBatteryPercent(pct)
            }
        }
    }
    private var batteryReceiverRegistered: Boolean = false

    fun boot(): ForegroundServiceCommand {
        if (monitorJob == null) {
            monitorJob = scope.launch {
                runtimePolicyMonitor.batteryPercent.collectLatest { pct ->
                    orchestrator.onBatteryLevelChanged(pct)
                }
            }
        }
        if (!batteryReceiverRegistered && context != null) {
            val safeContext = context
            safeContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
        }
        return foregroundServiceEntry.startService()
    }

    fun requestUserStartListening(): ForegroundServiceCommand {
        context?.let { ListeningStateStore.setListeningDesired(it, true) }
        (startServiceAction ?: {
            val safeContext = requireNotNull(context) { "context is required when startServiceAction is not provided" }
            val serviceIntent = Intent(safeContext, FinderForegroundService::class.java).apply {
                action = FinderForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(safeContext, serviceIntent)
        }).invoke()
        return foregroundServiceEntry.startService()
    }

    fun requestUserStopListening(): ForegroundServiceCommand {
        return stopService()
    }

    fun shutdown(): ForegroundServiceCommand {
        monitorJob?.cancel()
        monitorJob = null
        if (batteryReceiverRegistered && context != null) {
            context.unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }
        return stopService()
    }

    private fun stopService(): ForegroundServiceCommand {
        context?.let { ListeningStateStore.setListeningDesired(it, false) }
        (stopServiceAction ?: {
            val safeContext = requireNotNull(context) { "context is required when stopServiceAction is not provided" }
            val serviceIntent = Intent(safeContext, FinderForegroundService::class.java).apply {
                action = FinderForegroundService.ACTION_STOP
            }
            safeContext.startService(serviceIntent)
        }).invoke()
        return foregroundServiceEntry.stopService()
    }
}
