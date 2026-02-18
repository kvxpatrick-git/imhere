package com.imhere.app.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.imhere.app.data.repository.DataStoreSettingsRepository
import com.imhere.app.runtime.ListeningStateStore
import com.imhere.app.service.FinderForegroundService
import kotlinx.coroutines.runBlocking

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val settings = runBlocking {
            DataStoreSettingsRepository(context.applicationContext).get()
        }
        if (!settings.autoResumeOnBoot) return
        if (!ListeningStateStore.isListeningDesired(context)) return

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) return

        val serviceIntent = Intent(context, FinderForegroundService::class.java).apply {
            this.action = FinderForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
