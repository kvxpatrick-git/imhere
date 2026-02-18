package com.imhere.platform.monitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RuntimePolicyMonitor {
    private val _batteryPercent = MutableStateFlow(100)
    private val _hasMicPermission = MutableStateFlow(false)
    private val _notificationPermission = MutableStateFlow(false)
    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    private val _backgroundRestricted = MutableStateFlow(false)

    val batteryPercent: StateFlow<Int> = _batteryPercent
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission
    val notificationPermission: StateFlow<Boolean> = _notificationPermission
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored
    val backgroundRestricted: StateFlow<Boolean> = _backgroundRestricted

    fun updateBatteryPercent(value: Int) {
        _batteryPercent.value = value.coerceIn(0, 100)
    }

    fun updateMicPermission(granted: Boolean) {
        _hasMicPermission.value = granted
    }

    fun updateNotificationPermission(granted: Boolean) {
        _notificationPermission.value = granted
    }

    fun updateBatteryOptimizationIgnored(ignored: Boolean) {
        _batteryOptimizationIgnored.value = ignored
    }

    fun updateBackgroundRestricted(restricted: Boolean) {
        _backgroundRestricted.value = restricted
    }
}
