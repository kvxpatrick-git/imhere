package com.imhere.app.ui.screens

import androidx.compose.runtime.Composable
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.RuntimePolicyMonitor

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DebugToolsSection(
    runtimePolicyMonitor: RuntimePolicyMonitor,
    orchestrator: FinderOrchestrator,
    batteryPercent: Int,
    onRefresh: () -> Unit
) = Unit
