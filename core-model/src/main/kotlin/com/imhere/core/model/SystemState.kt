package com.imhere.core.model

sealed interface SystemState {
    data object IDLE : SystemState
    data object LISTENING : SystemState
    data object PENDING_CONFIRMATION : SystemState
    data class TRIGGERED(val stage: Int) : SystemState
    data object STOPPING : SystemState
    data object STOPPED : SystemState
    data class ERROR(val recoverable: Boolean, val code: String?) : SystemState
}
