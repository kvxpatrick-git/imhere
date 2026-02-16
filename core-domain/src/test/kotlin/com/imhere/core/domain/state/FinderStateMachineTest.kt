package com.imhere.core.domain.state

import com.imhere.core.model.SystemState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FinderStateMachineTest {
    @Test
    fun twoStepFlow_transitionsToPendingThenTriggered() {
        val machine = FinderStateMachine()

        machine.startListening()
        assertEquals(SystemState.PENDING_CONFIRMATION, machine.onWakeDetected(twoStepEnabled = true))

        val confirmed = machine.onConfirmDetected()
        assertIs<SystemState.TRIGGERED>(confirmed)
        assertEquals(1, confirmed.stage)
    }

    @Test
    fun stageTimeout_progressesAndStopsAtStage3() {
        val machine = FinderStateMachine()
        machine.startListening()
        machine.onWakeDetected(twoStepEnabled = false)

        val stage2 = machine.onStageTimeout()
        assertIs<SystemState.TRIGGERED>(stage2)
        assertEquals(2, stage2.stage)

        val stage3 = machine.onStageTimeout()
        assertIs<SystemState.TRIGGERED>(stage3)
        assertEquals(3, stage3.stage)

        assertEquals(SystemState.STOPPING, machine.onStageTimeout())
    }

    @Test
    fun recoverableError_becomesFatalAfterRetryLimit() {
        val machine = FinderStateMachine(maxRecoverableRetries = 2)

        val err1 = machine.onError(code = "E-DET-001", recoverable = true)
        assertIs<SystemState.ERROR>(err1)
        assertEquals(true, err1.recoverable)

        val err2 = machine.onError(code = "E-DET-001", recoverable = true)
        assertIs<SystemState.ERROR>(err2)
        assertEquals(true, err2.recoverable)

        val err3 = machine.onError(code = "E-DET-001", recoverable = true)
        assertIs<SystemState.ERROR>(err3)
        assertEquals(false, err3.recoverable)
    }
}
