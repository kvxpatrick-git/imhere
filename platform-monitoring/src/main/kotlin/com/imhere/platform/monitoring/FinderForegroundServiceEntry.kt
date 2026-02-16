package com.imhere.platform.monitoring

enum class ForegroundServiceCommand {
    START_REQUESTED,
    STOP_REQUESTED
}

class FinderForegroundServiceEntry {
    fun startService(): ForegroundServiceCommand = ForegroundServiceCommand.START_REQUESTED
    fun stopService(): ForegroundServiceCommand = ForegroundServiceCommand.STOP_REQUESTED
}
