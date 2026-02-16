package com.imhere.core.domain.state

import com.imhere.core.model.SystemState

class FinderStateMachine(
    private val maxRecoverableRetries: Int = 3
) {
    private var state: SystemState = SystemState.IDLE
    private var recoverableErrorCount: Int = 0

    fun currentState(): SystemState = state

    fun startListening(): SystemState {
        state = SystemState.LISTENING
        recoverableErrorCount = 0
        return state
    }

    fun onWakeDetected(twoStepEnabled: Boolean): SystemState {
        state = if (twoStepEnabled) {
            SystemState.PENDING_CONFIRMATION
        } else {
            SystemState.TRIGGERED(stage = 1)
        }
        return state
    }

    fun onConfirmationTimedOut(): SystemState {
        if (state == SystemState.PENDING_CONFIRMATION) {
            state = SystemState.LISTENING
        }
        return state
    }

    fun onConfirmDetected(): SystemState {
        if (state == SystemState.PENDING_CONFIRMATION) {
            state = SystemState.TRIGGERED(stage = 1)
        }
        return state
    }

    fun onStageTimeout(): SystemState {
        val current = state
        if (current is SystemState.TRIGGERED) {
            state = when (current.stage) {
                1 -> SystemState.TRIGGERED(stage = 2)
                2 -> SystemState.TRIGGERED(stage = 3)
                else -> SystemState.STOPPING
            }
        }
        return state
    }

    fun onStopRequested(): SystemState {
        state = SystemState.STOPPING
        return state
    }

    fun onStopped(): SystemState {
        state = SystemState.STOPPED
        return state
    }

    fun onRecoveryComplete(): SystemState {
        state = SystemState.LISTENING
        return state
    }

    fun onError(code: String, recoverable: Boolean): SystemState {
        if (recoverable) {
            recoverableErrorCount += 1
        }
        val canRecover = recoverable && recoverableErrorCount <= maxRecoverableRetries
        state = SystemState.ERROR(recoverable = canRecover, code = code)
        return state
    }
}
