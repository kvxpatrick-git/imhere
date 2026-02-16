# API/인터페이스 명세서
## 음성 호출 기반 스마트폰 찾기 앱 Android MVP

## 1. 문서 정보
- 문서명: API/Interface Specification
- 버전: v1.0
- 작성일: 2026-02-15
- 기준 문서
- `docs/prd-voice-phone-finder-mvp.md`
- `docs/technical-spec-voice-phone-finder-mvp.md`
- `docs/technical-design-voice-phone-finder-mvp.md`

## 2. 목적 및 범위
본 문서는 내부 모듈 간 계약을 고정한다. 구현체가 달라도 인터페이스와 이벤트 계약이 동일하면 상호 교체 가능해야 한다.

포함 범위:
- VoiceDetectionEngine (온디바이스 감지 엔진 포트)
- AlertPlayer (알림 재생 포트)
- PolicyManager (트리거/배터리/강제재생 정책)
- SettingsRepository / SessionRepository (저장소 포트)
- Orchestrator 입력/출력 이벤트
- 표준 에러코드 및 매핑

## 3. 공통 타입
```kotlin
enum class SystemState {
    IDLE,
    LISTENING,
    PENDING_CONFIRMATION,
    TRIGGERED,
    STOPPING,
    STOPPED,
    ERROR
}

enum class StopReason {
    VOICE_STOP,
    USER_STOP,
    STAGE3_TIMEOUT,
    CALL_INTERRUPTION,
    AUDIO_FOCUS_LOST,
    ENGINE_FAILURE,
    POLICY_BLOCKED
}

enum class EngineProfile {
    BALANCED,
    POWER_SAVE
}

data class DetectionConfig(
    val keywords: List<String>,
    val stopPhrases: List<String>,
    val confirmPhrase: String,
    val twoStepTriggerEnabled: Boolean,
    val confidenceThreshold: Float,
    val profile: EngineProfile,
    val cooldownSec: Int
)

data class PlaybackPolicy(
    val forcePlaybackEnabled: Boolean,
    val stage1Sec: Int = 2,
    val stage2Sec: Int = 10,
    val stage3MaxSec: Int = 60,
    val stage1Volume: Int = 30,
    val stage2Volume: Int = 70,
    val stage3Volume: Int = 100,
    val vibratePatternMs: LongArray = longArrayOf(0, 700, 300, 700, 300)
)
```

## 4. 모듈 인터페이스

### 4.1 VoiceDetectionEngine
```kotlin
interface VoiceDetectionEngine {
    suspend fun start(config: DetectionConfig)
    suspend fun stop()
    fun updateKeywords(keywords: List<String>)

    val wakeEvents: Flow<WakeDetected>
    val stopEvents: Flow<StopDetected>
    val errors: Flow<DetectionError>
}
```

입력:
- `start(config)`: 감지 시작
- `updateKeywords(keywords)`: 키워드 변경 반영
- `stop()`: 감지 중단

출력 이벤트:
```kotlin
data class WakeDetected(val keyword: String, val confidence: Float, val atEpochMs: Long)
data class StopDetected(val phrase: String, val confidence: Float, val atEpochMs: Long)
data class DetectionError(val code: ErrorCode, val recoverable: Boolean, val message: String)
```

호출 계약:
- 단일 직렬 실행 보장
- `start()` idempotent
- `stop()` no-op 안전
- `updateKeywords()` 원자적 스왑(반영 중 기존 스냅샷 유지)
- 경쟁 호출 시 우선순위 `stop > start > updateKeywords`

### 4.2 AlertPlayer
```kotlin
interface AlertPlayer {
    suspend fun playSequence(policy: PlaybackPolicy): PlayResult
    suspend fun stop(reason: StopReason)
    fun isPlaying(): Boolean
}

data class PlayResult(
    val startedAtEpochMs: Long,
    val firstAudioAtEpochMs: Long,
    val volumeOverrideApplied: Boolean
)
```

입력:
- `playSequence(policy)`: 단계형 재생 시작
- `stop(reason)`: 즉시 종료 + 원복

출력:
- 지연 측정용 시각 정보

보장:
- `stop()` 호출 시 1초 내 사운드 정지(P95)
- 종료 시 원래 볼륨 복원

### 4.3 PolicyManager
```kotlin
interface PolicyManager {
    fun evaluateTrigger(input: TriggerInput): TriggerDecision
    fun selectEngineProfile(batteryPct: Int, saverEnabled: Boolean): EngineProfile
    fun canStartListening(userInitiated: Boolean, hasMicPermission: Boolean): StartDecision
    fun selectPlaybackPolicy(settings: UserSettings, isSilentMode: Boolean): PlaybackPolicy
}

data class TriggerInput(
    val twoStepEnabled: Boolean,
    val hasWake: Boolean,
    val hasConfirm: Boolean,
    val confirmWithinSec: Int,
    val cooldownRemainingSec: Int
)

sealed interface TriggerDecision {
    data object StartSession : TriggerDecision
    data object WaitForConfirm : TriggerDecision
    data class Reject(val reason: PolicyRejectReason) : TriggerDecision
}

sealed interface StartDecision {
    data object Allow : StartDecision
    data class Block(val reason: PolicyRejectReason) : StartDecision
}

enum class PolicyRejectReason {
    COOLDOWN,
    MISSING_CONFIRM,
    BATTERY_HARD_BLOCK,
    PERMISSION_NOT_GRANTED,
    BACKGROUND_START_RESTRICTED
}
```

### 4.4 SettingsRepository
```kotlin
interface SettingsRepository {
    suspend fun get(): UserSettings
    val settingsFlow: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

data class UserSettings(
    val stopPhrases: List<String>,
    val confirmPhrase: String,
    val forcePlaybackEnabled: Boolean,
    val twoStepTriggerEnabled: Boolean,
    val batterySaverEnabled: Boolean,
    val batteryThresholdPercent: Int
)
```

### 4.5 SessionRepository
```kotlin
interface SessionRepository {
    suspend fun create(session: FindSession)
    suspend fun updateStage(sessionId: String, stage: Int)
    suspend fun close(sessionId: String, reason: StopReason, stopLatencyMs: Long?)
    suspend fun listRecent(limit: Int): List<FindSession>
    suspend fun purgeExpired(nowEpochMs: Long)
}

data class FindSession(
    val id: String,
    val triggeredKeywordProtected: String,
    val triggerConfidence: Float?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val stageReached: Int,
    val stopReason: StopReason?,
    val forcePlaybackApplied: Boolean,
    val batterySaverActive: Boolean,
    val detectionLatencyMs: Long?,
    val stopLatencyMs: Long?
)
```

### 4.6 FinderOrchestrator Port
```kotlin
interface FinderOrchestrator {
    val state: StateFlow<SystemState>
    val uiEvents: SharedFlow<UiEvent>

    suspend fun startListening(userInitiated: Boolean)
    suspend fun stopListening()
    suspend fun onWakeDetected(event: WakeDetected)
    suspend fun onStopDetected(event: StopDetected)
}

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    data class Error(val code: ErrorCode, val message: String) : UiEvent
    data object ReadyToResume : UiEvent
}
```

## 5. I/O 데이터 계약

### 5.1 키워드 입력 검증
- 최소 2자, 최대 12자
- 중복 불가
- 금지 단어(`설정`, `취소`, `종료`) 불가
- 중지 문구(`멈춰` 계열)와 동일 문구 금지

### 5.2 세션 출력 데이터
- `session_id`: UUID v4
- `triggered_keyword`: 평문 저장 금지. 허용값은 `masked` 또는 `encrypted` 표현만 허용
- `detection_latency_ms`: 감지 시각부터 1단계 오디오 시작까지
- `stage_reached`: 1~3
- `stop_reason`: `StopReason`

### 5.3 정책 출력
- 트리거 승인/거절 사유 반환 필수
- 거절 시 사용자 메시지와 내부 코드 매핑

### 5.4 snake_case <-> camelCase 매핑 규칙
- 도메인/코드 모델은 `camelCase`를 사용한다. (예: `triggeredKeywordProtected`)
- DB/DataStore 키는 `snake_case`를 사용한다. (예: `triggered_keyword`)
- 매핑 책임은 Data Mapper 계층이 가진다.
- 매핑은 의미가 동일해야 하며, 평문 금지/암호화 정책은 저장 계층 규칙을 따른다.

## 6. 에러코드 표준

### 6.1 코드 체계
- 오디오: `E-AUD-*`
- 감지엔진: `E-DET-*`
- 재생: `E-PLY-*`
- 설정/저장: `E-SET-*`, `E-DB-*`
- 정책/권한: `E-POL-*`, `E-PRM-*`

### 6.2 상세 목록
| 코드 | 발생 지점 | 설명 | 복구 정책 |
|---|---|---|---|
| E-AUD-001 | VoiceDetectionEngine.start | 마이크 권한 없음 | 권한 요청 UI 노출 |
| E-AUD-002 | VoiceDetectionEngine.start | 마이크 점유 충돌 | 3회 재시도 후 실패 |
| E-DET-001 | VoiceDetectionEngine.start | 엔진 초기화 실패 | 1s/2s/4s 재시도 |
| E-DET-002 | VoiceDetectionEngine.updateKeywords | 모델/키워드 로드 실패 | 기존 스냅샷 유지 |
| E-PLY-001 | AlertPlayer.playSequence | 오디오 포커스 획득 실패 | 300ms*3 재시도 |
| E-PLY-002 | AlertPlayer.playSequence | 재생 리소스 로드 실패 | 세션 중단 |
| E-SET-001 | SettingsRepository.update | 잘못된 키워드 입력 | 저장 거부 + 오류 노출 |
| E-DB-001 | SessionRepository.create | DB 쓰기 실패 | 메모리 큐 후 재시도 |
| E-POL-001 | PolicyManager.evaluateTrigger | 쿨다운 위반 | 트리거 거부 |
| E-PRM-001 | startListening | Android 14+ 백그라운드 시작 제한 | READY_TO_RESUME 전환 |

### 6.3 오류 전달 규칙
- Domain 내부: `ErrorCode` + `recoverable` + `cause`
- UI: 사용자 문구는 친화적으로, 내부 코드는 별도 로그
- 동일 오류 3회 연속 시 서킷 오픈(`DEGRADED`)

```kotlin
enum class ErrorCode {
    E_AUD_001,
    E_AUD_002,
    E_DET_001,
    E_DET_002,
    E_PLY_001,
    E_PLY_002,
    E_SET_001,
    E_DB_001,
    E_POL_001,
    E_PRM_001
}
```

## 7. 상태/이벤트 시퀀스 계약
1. `LISTENING`에서 `WakeDetected` 수신
2. `PolicyManager.evaluateTrigger`
3. 승인 시 `TRIGGERED(stage1)` + `AlertPlayer.playSequence`
4. `StopDetected` 수신 시 `STOPPING` -> `STOPPED` -> `LISTENING`
5. Android 14+ 정책 위반 시작 시 `UiEvent.ReadyToResume`

## 8. 버전/호환성 정책
- 인터페이스 변경은 semver 준수
- 메서드 시그니처 파괴적 변경은 `v2` 브랜치 또는 adapter layer 제공
- 이벤트 필드 추가는 optional 우선

## 9. 검증 체크리스트
- [ ] 인터페이스별 입력/출력/오류코드 명시
- [ ] 상태머신과 이벤트 명세 일치
- [ ] 기술명세서/기술설계서와 코드값 일치
- [ ] Android 14+ 시작 제한 정책 반영
