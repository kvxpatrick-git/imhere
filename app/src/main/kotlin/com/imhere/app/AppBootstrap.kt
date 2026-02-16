package com.imhere.app

import com.imhere.platform.monitoring.FinderForegroundServiceEntry
import com.imhere.platform.monitoring.ForegroundServiceCommand

class AppBootstrap(
    private val foregroundServiceEntry: FinderForegroundServiceEntry = FinderForegroundServiceEntry()
) {
    fun boot(): ForegroundServiceCommand {
        return foregroundServiceEntry.startService()
    }
}
