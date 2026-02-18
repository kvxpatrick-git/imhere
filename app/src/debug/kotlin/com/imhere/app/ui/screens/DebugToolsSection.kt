package com.imhere.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import kotlinx.coroutines.launch

@Composable
internal fun DebugToolsSection(
    runtimePolicyMonitor: RuntimePolicyMonitor,
    orchestrator: FinderOrchestrator,
    batteryPercent: Int,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("진단 도구", style = MaterialTheme.typography.titleLarge)
            Text("디버그 빌드 전용 기능입니다.")

            OutlinedButton(onClick = { runtimePolicyMonitor.updateMicPermission(true) }) {
                Text("권한 허용 시뮬레이션")
            }
            OutlinedButton(onClick = { runtimePolicyMonitor.updateMicPermission(false) }) {
                Text("권한 거부 시뮬레이션")
            }
            OutlinedButton(onClick = {
                val nextBattery = (batteryPercent - 10).coerceAtLeast(0)
                runtimePolicyMonitor.updateBatteryPercent(nextBattery)
                scope.launch {
                    orchestrator.onBatteryLevelChanged(nextBattery)
                    onRefresh()
                }
            }) {
                Text("배터리 -10%")
            }
        }
    }
}
