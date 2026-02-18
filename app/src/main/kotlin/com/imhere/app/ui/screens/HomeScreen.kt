package com.imhere.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.imhere.app.AppBootstrap
import com.imhere.core.domain.port.SessionRepository
import com.imhere.core.model.SystemState
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import com.imhere.app.ui.theme.Clay
import com.imhere.app.ui.theme.Paper
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun HomeRoute(onOpenSettings: () -> Unit) {
    val orchestrator = koinInject<FinderOrchestrator>()
    val appBootstrap = koinInject<AppBootstrap>()
    val sessionRepository = koinInject<SessionRepository>()
    val runtimePolicyMonitor = koinInject<RuntimePolicyMonitor>()
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(orchestrator.currentState()) }
    var recentSessions by remember { mutableStateOf<List<String>>(emptyList()) }
    val batteryPercent by runtimePolicyMonitor.batteryPercent.collectAsState()
    val hasMicPermission by runtimePolicyMonitor.hasMicPermission.collectAsState()
    val hasNotificationPermission by runtimePolicyMonitor.notificationPermission.collectAsState()
    val batteryOptimizationIgnored by runtimePolicyMonitor.batteryOptimizationIgnored.collectAsState()
    val backgroundRestricted by runtimePolicyMonitor.backgroundRestricted.collectAsState()
    val statusText = state.asDebugLabel()

    fun refreshStateAndLogs() {
        state = orchestrator.currentState()
        scope.launch {
            recentSessions = sessionRepository.listRecent(limit = 5).map { session ->
                "${session.id.take(8)} | stage ${session.stageReached} | ${session.stopReason ?: "RUNNING"}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, Clay, Paper)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("ImHere", style = MaterialTheme.typography.headlineLarge)
        Text("목소리로 내 폰 위치 찾기", style = MaterialTheme.typography.titleLarge)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 상태: $statusText")
                Text("마이크 권한: ${if (hasMicPermission) "허용" else "미허용"}")
                Text("배터리: ${batteryPercent}%")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    scope.launch {
                        appBootstrap.requestUserStartListening()
                        refreshStateAndLogs()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("리스닝 시작") }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        appBootstrap.requestUserStopListening()
                        refreshStateAndLogs()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("리스닝 중지") }
        }

        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("설정 열기")
        }

        PolicyGuidanceSection(
            hasMicPermission = hasMicPermission,
            hasNotificationPermission = hasNotificationPermission,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            backgroundRestricted = backgroundRestricted
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("최근 세션", style = MaterialTheme.typography.titleLarge)
                if (recentSessions.isEmpty()) {
                    Text("세션 기록이 없습니다.")
                } else {
                    recentSessions.forEach { Text(it) }
                }
                OutlinedButton(onClick = { refreshStateAndLogs() }) {
                    Text("새로고침")
                }
            }
        }
        DebugToolsSection(
            runtimePolicyMonitor = runtimePolicyMonitor,
            orchestrator = orchestrator,
            batteryPercent = batteryPercent,
            onRefresh = { refreshStateAndLogs() }
        )
    }
}

@Composable
private fun PolicyGuidanceSection(
    hasMicPermission: Boolean,
    hasNotificationPermission: Boolean,
    batteryOptimizationIgnored: Boolean,
    backgroundRestricted: Boolean
) {
    val context = LocalContext.current
    val needsAttention =
        !hasMicPermission ||
            !hasNotificationPermission ||
            !batteryOptimizationIgnored ||
            backgroundRestricted
    if (!needsAttention) return

    fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestIgnoreBatteryOptimization() {
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(requestIntent) }
            .onFailure { context.startActivity(fallbackIntent) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("실행 제약 감지", style = MaterialTheme.typography.titleLarge)
            Text("안정적인 백그라운드 감지를 위해 아래 항목을 확인하세요.")

            if (!hasMicPermission) {
                Text("• 마이크 권한이 거부됨")
                OutlinedButton(onClick = { openAppDetailsSettings() }) {
                    Text("앱 권한 설정 열기")
                }
            }
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text("• 알림 권한이 꺼져 있음 (포그라운드 서비스 안내 필요)")
                OutlinedButton(onClick = { openNotificationSettings() }) {
                    Text("알림 설정 열기")
                }
            }
            if (!batteryOptimizationIgnored) {
                Text("• 배터리 최적화 예외 미적용")
                OutlinedButton(onClick = { requestIgnoreBatteryOptimization() }) {
                    Text("배터리 최적화 예외 요청")
                }
            }
            if (backgroundRestricted) {
                Text("• 시스템 백그라운드 제한 활성화")
                OutlinedButton(onClick = { openAppDetailsSettings() }) {
                    Text("앱 배터리 제한 확인")
                }
            }

            deviceSpecificBatteryHint()?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun deviceSpecificBatteryHint(): String? {
    return when (Build.MANUFACTURER.lowercase(Locale.getDefault())) {
        "samsung" -> "Samsung: 설정 > 배터리 > 백그라운드 사용 제한에서 ImHere를 제한 대상에서 제외하세요."
        "xiaomi" -> "Xiaomi: 보안/배터리 앱에서 자동 시작 허용 및 배터리 최적화 제외를 설정하세요."
        "oppo", "oneplus", "realme" -> "OPPO/OnePlus/realme: 배터리 최적화에서 ImHere를 '제한 없음'으로 설정하세요."
        "vivo" -> "vivo: iManager에서 백그라운드 자동 시작과 배터리 예외를 허용하세요."
        else -> null
    }
}

private fun SystemState.asDebugLabel(): String = when (this) {
    SystemState.IDLE -> "IDLE"
    SystemState.LISTENING -> "LISTENING"
    SystemState.PENDING_CONFIRMATION -> "PENDING_CONFIRMATION"
    is SystemState.TRIGGERED -> "TRIGGERED(stage=$stage)"
    SystemState.STOPPING -> "STOPPING"
    SystemState.STOPPED -> "STOPPED"
    is SystemState.ERROR -> "ERROR(recoverable=$recoverable, code=$code)"
}
