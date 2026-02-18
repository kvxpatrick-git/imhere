package com.imhere.app.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imhere.core.domain.port.KeywordRepository
import com.imhere.core.domain.port.SettingsRepository
import com.imhere.core.model.UserSettings
import com.imhere.feature.listening.FinderOrchestrator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val keywordRepository = koinInject<KeywordRepository>()
    val orchestrator = koinInject<FinderOrchestrator>()
    val scope = rememberCoroutineScope()

    var forcePlaybackEnabled by remember { mutableStateOf(true) }
    var twoStepTriggerEnabled by remember { mutableStateOf(false) }
    var batterySaverEnabled by remember { mutableStateOf(true) }
    var autoResumeOnBoot by remember { mutableStateOf(false) }
    var confirmPhrase by remember { mutableStateOf("여기 있어") }
    var keywordInput by remember { mutableStateOf("폰아 울려") }
    var keywordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val current = settingsRepository.get()
        val keywords = keywordRepository.get()
        forcePlaybackEnabled = current.forcePlaybackEnabled
        twoStepTriggerEnabled = current.twoStepTriggerEnabled
        batterySaverEnabled = current.batterySaverEnabled
        autoResumeOnBoot = current.autoResumeOnBoot
        confirmPhrase = current.confirmPhrase
        keywordInput = if (keywords.isEmpty()) "폰아 울려" else keywords.joinToString(", ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineLarge)
        Text("앱 동작 정책을 조정합니다.", style = MaterialTheme.typography.bodyLarge)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("재생 정책", style = MaterialTheme.typography.titleLarge)
                SettingToggle("무음에서도 강제 재생", forcePlaybackEnabled) {
                    forcePlaybackEnabled = it
                }
                SettingToggle("2단계 트리거 사용", twoStepTriggerEnabled) {
                    twoStepTriggerEnabled = it
                }
                SettingToggle("배터리 보호 모드", batterySaverEnabled) {
                    batterySaverEnabled = it
                }
                SettingToggle("재부팅 후 자동 재시작", autoResumeOnBoot) {
                    autoResumeOnBoot = it
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("음성 문구", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = confirmPhrase,
                    onValueChange = {
                        confirmPhrase = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("확인 문구") }
                )
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = {
                        keywordInput = it
                        keywordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("호출 키워드 (쉼표 구분, 1~3개)") }
                )
                keywordError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    scope.launch {
                        val parsedKeywords = keywordInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                        keywordError = validateKeywords(parsedKeywords)
                        if (keywordError != null) return@launch

                        val next = UserSettings(
                            stopPhrases = listOf("멈춰"),
                            forcePlaybackEnabled = forcePlaybackEnabled,
                            twoStepTriggerEnabled = twoStepTriggerEnabled,
                            confirmPhrase = confirmPhrase.trim().ifEmpty { "여기 있어" },
                            batterySaverEnabled = batterySaverEnabled,
                            batteryThresholdPercent = 20,
                            autoResumeOnBoot = autoResumeOnBoot
                        )
                        settingsRepository.update { next }
                        keywordRepository.saveKeywords(parsedKeywords)
                        orchestrator.updateRuntimeSettings(next)
                        orchestrator.updateWakeKeywords(parsedKeywords)
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("저장")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("뒤로")
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun validateKeywords(keywords: List<String>): String? {
    if (keywords.isEmpty()) return "키워드는 최소 1개가 필요합니다."
    if (keywords.size > 3) return "키워드는 최대 3개까지 가능합니다."
    if (keywords.any { it.length !in 2..12 }) return "각 키워드는 2~12자여야 합니다."
    return null
}
