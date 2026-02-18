package com.imhere.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Surface
import com.imhere.app.ui.navigation.ImHereNavHost
import com.imhere.app.ui.theme.ImHereTheme
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val runtimePolicyMonitor: RuntimePolicyMonitor by inject()

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            runtimePolicyMonitor.updateMicPermission(granted)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            runtimePolicyMonitor.updateNotificationPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncRuntimePolicyState()
        requestRequiredPermissions()
        setContent {
            ImHereTheme {
                Surface {
                    ImHereNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncRuntimePolicyState()
    }

    private fun syncRuntimePolicyState() {
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        runtimePolicyMonitor.updateMicPermission(micGranted)

        val notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        runtimePolicyMonitor.updateNotificationPermission(notificationGranted)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        runtimePolicyMonitor.updateBatteryOptimizationIgnored(
            powerManager.isIgnoringBatteryOptimizations(packageName)
        )

        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isBackgroundRestricted
        } else {
            false
        }
        runtimePolicyMonitor.updateBackgroundRestricted(backgroundRestricted)
    }

    private fun requestRequiredPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
