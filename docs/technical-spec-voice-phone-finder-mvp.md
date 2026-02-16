# 기술명세서 (Technical Specification)
## 음성 호출 기반 스마트폰 찾기 앱 Android MVP

## 1. 문서 메타데이터
- 문서명: Technical Specification
- 문서 버전: v1.0
- 작성일: 2026-02-15
- 기준 PRD: `docs/prd-voice-phone-finder-mvp.md`
- 대상 릴리즈: MVP v1.0
- 대상 플랫폼: Android 10 (API 29) 이상 권장, Android 8 (API 26) 이상 최소

## 2. 문서 목적
이 문서는 PRD의 기능 요구사항(FR-01~FR-08)을 구현 가능한 수준으로 세분화하고, 입력/출력 계약, 정책 규칙, 예외 처리, 수용 기준을 명시한다. 구현자는 본 문서만으로 기능 동작을 재현 가능해야 한다.

## 3. 범위
### 3.1 포함
- 온디바이스 음성 인식 기반 호출 키워드 감지
- 사용자 커스텀 키워드/별칭 관리
- 3단계 알림 재생 시퀀스
- 음성 중지 명령 처리
- 무음 강제 재생 옵션
- 2단계 트리거(오탐 방지)
- 배터리 보호 모드
- 로컬 로그 저장/조회

### 3.2 제외
- 스마트홈, 가족 계정, 웨어러블 연동
- 클라우드 음성 처리
- 위치/공간(방) 기반 정책

## 4. 용어 정의
- 세션(Session): 키워드 감지부터 알림 중지까지의 단일 찾기 사이클
- 웨이크 키워드(Wake Keyword): 찾기 시작을 트리거하는 사용자 문구
- 중지 키워드(Stop Keyword): 재생 중지 문구 (기본: `멈춰`)
- 2단계 트리거: 웨이크 키워드 + 확인 문구가 연속 일치해야 트리거되는 모드
- 보호 모드: 저전력 시 감지 빈도/정확도 프로파일을 절전형으로 낮추는 상태
- 강제 재생: 무음/진동 상태에서도 알림 사운드를 재생하는 정책

## 5. 시스템 상태 모델
앱 런타임 상태는 다음 7개로 정의한다.

1. `IDLE`
- 리스닝 비활성
- 마이크 점유 없음

2. `LISTENING`
- 리스닝 활성
- 웨이크 키워드 대기

3. `PENDING_CONFIRMATION`
- 2단계 트리거 ON일 때 확인 문구 대기 상태
- 웨이크 감지 후 최대 4초 유지

4. `TRIGGERED`
- 웨이크 키워드 감지 완료
- 3단계 재생 시퀀스 동작 중

5. `STOPPING`
- 중지 명령/타임아웃 후 종료 처리 중간 상태
- 오디오/진동 정지 및 원상 복구 수행

6. `STOPPED`
- 음성 중지 혹은 타임아웃/사용자 중지로 세션 종료
- 후처리(로그 저장/오디오 복원) 수행

7. `ERROR`
- 감지 엔진, 오디오 경로 등 오류 상태
- 복구 가능 오류는 백오프 재시도 후 `LISTENING` 복귀

`BATTERY_SAVER_ACTIVE`는 상태가 아니라 `LISTENING`에 중첩되는 보조 플래그로 정의한다.

## 6. 기능 명세 상세

### 6.1 FR-01 온디바이스 키워드 감지
#### 요구사항
- 네트워크 연결 없이 웨이크 키워드를 감지해야 한다.
- 백그라운드에서도 동작해야 하며, 포그라운드 서비스 알림을 유지해야 한다.

#### 입력
- 마이크 PCM 스트림
- 사용자 키워드 목록(1~3개)
- 인식 민감도 프로파일(`balanced` 또는 `power_save`)

#### 출력
- `WakeDetected` 이벤트
- 감지 실패/엔진 오류 시 `DetectionError` 이벤트

#### 동작 규칙
- 리스닝 시작 후 준비 완료까지 최대 5초
- 감지 확률(Confidence) 임계치 기본값 0.62
- 동일 문구 재트리거 방지 쿨다운: 15초

#### 예외
- 마이크 권한 미허용: 리스닝 시작 차단 + 권한 가이드 노출
- 엔진 초기화 실패: 재시도 3회 후 `IDLE` 전환

#### 수용 기준
- 비행기 모드에서 웨이크 키워드 감지 성공
- 백그라운드 상태에서 10회 연속 감지 성공률 90% 이상

### 6.2 FR-02 사용자 키워드/별칭 관리
#### 요구사항
- 키워드 1~3개 등록 가능
- 변경 즉시 감지 엔진에 반영

#### 입력 검증 규칙
- 길이: 2~12자
- 공백 trim 후 빈 문자열 불가
- 중복 키워드 불가
- 금지 목록 포함 문구 불가
- 발음 유사도 기준으로 기존 키워드와 충돌 시 경고

#### 금지 목록 기본값
- 시스템 명령 충돌어: `설정`, `취소`, `종료`
- 중지 키워드와 동일 문구 (`멈춰`)

#### 반영 규칙
- 저장 성공 후 10초 이내 감지 엔진 reload 완료
- reload 중에도 기존 키워드로 감지 지속(무중단 교체)

#### 수용 기준
- 등록/수정/삭제 각각 10초 내 반영
- 삭제된 키워드가 더 이상 트리거되지 않음

### 6.3 FR-03 즉시 알림 재생
#### 요구사항
- 웨이크 감지 후 자동으로 재생 시작

#### 성능 기준
- 감지 이벤트 시각 `T0`
- 1단계 사운드 재생 시작 `T1`
- 제약: `T1 - T0 <= 1.5s (P95)`

#### 예외
- 오디오 포커스 획득 실패: 300ms 간격 3회 재시도 후 강제 재생 옵션 판단

### 6.4 FR-04 3단계 재생 시퀀스
#### 단계 정의
- Stage 1: 2초 확인음, 볼륨 30%
- Stage 2: 10초 벨소리, 볼륨 70%
- Stage 3: 최대 60초 벨소리 반복 + 진동, 볼륨 100%

#### 전환 정책
- Stage1 종료 즉시 Stage2
- Stage2 종료 즉시 Stage3
- 사용자 중지 명령 수신 시 즉시 종료
- Stage3 최대 지속시간 초과 시 자동 종료

#### 볼륨 복원 정책
- 세션 시작 전 원래 볼륨 캡처
- 세션 종료 후 1초 내 원래 볼륨 복원

#### 진동 패턴
- 패턴: `[0, 700, 300, 700, 300]` 반복
- 진동 권한 없으면 무진동 대체(사운드는 유지)

#### 수용 기준
- 단계 전환 시간 오차 ±1초 이내
- Stage3 자동 종료 상한 60초 준수

### 6.5 FR-05 음성 중지 명령
#### 요구사항
- 재생 중 중지 키워드 인식 시 즉시 중단

#### 입력
- 기본 중지 문구: `멈춰`
- 확장 중지 문구(옵션): `정지`, `그만`

#### 정책
- 중지 인식은 `TRIGGERED` 상태에서만 활성
- 중지 인식 타임아웃: 세션 종료 시까지

#### 성능 기준
- 중지 문구 인식 이후 1초 이내 사운드 중지(P95)

### 6.6 FR-06 무음/진동 강제 재생 옵션
#### 요구사항
- 기본 ON
- ON 시 무음/진동 상태에서도 세션 재생 허용

#### 세부 정책
- 강제 재생 ON
- 알림/미디어 볼륨을 세션용 임시값으로 상승
- DND(방해금지) 상태는 OS 정책 범위 내에서 가능한 채널 우선 사용

#### OFF 정책
- 기기 음량 정책 준수, 무음이면 무음 유지

#### 수용 기준
- 무음 상태 + ON에서 사운드 가청 확인

### 6.7 FR-07 오탐 방지 옵션 (2단계 트리거)
#### 요구사항
- 옵션 OFF: 1단계 키워드만으로 트리거
- 옵션 ON: 키워드 + 확인 문구 일치 필요

#### 확인 문구 기본값
- `여기 있어`

#### 타이밍 규칙
- 웨이크 감지 후 4초 내 확인 문구 수신 필요
- 4초 초과 시 세션 폐기하고 `LISTENING` 복귀

#### 수용 기준
- ON에서 웨이크 단독 발화는 트리거되지 않아야 함

### 6.8 FR-08 배터리 보호 모드
#### 요구사항
- 배터리 20% 이하 자동 활성

#### 동작
- 감지 민감도 0.62 -> 0.70 (오탐 축소)
- 오디오 프레임 처리 주기 완화
- UI 상태 배지 표시

#### 해제
- 배터리 25% 이상 시 자동 해제 (히스테리시스 적용)

#### 수용 기준
- 임계치 진입 후 30초 내 모드 전환

## 7. 화면 및 UX 명세

### 7.1 홈 화면
- 필수 요소
- 리스닝 상태 토글
- 현재 상태 칩 (`IDLE/LISTENING/TRIGGERED`)
- 마지막 찾기 시각
- 빠른 테스트 버튼

### 7.2 키워드 관리 화면
- 키워드 리스트(최대 3)
- 등록/수정/삭제
- 유효성 검증 메시지 실시간 표시
- 저장 후 즉시 반영 상태 표시(`엔진 반영 중/완료`)

### 7.3 설정 화면
- 강제 재생 ON/OFF
- 오탐 방지(2단계 트리거) ON/OFF
- 배터리 보호 모드 ON/OFF(자동 추천)
- 중지 문구 확장 사용 ON/OFF

### 7.4 테스트 화면
- 키워드 감지 테스트
- 단계별 사운드 샘플 청취
- 권한 진단 카드

### 7.5 접근성 화면
- 큰 글씨 모드
- 단순 UI 모드
- 고대비 색상 옵션

## 8. 내부 인터페이스 명세

### 8.1 도메인 이벤트
```kotlin
data class WakeDetected(
    val keyword: String,
    val confidence: Float,
    val detectedAtEpochMs: Long
)

data class StopDetected(
    val phrase: String,
    val confidence: Float,
    val detectedAtEpochMs: Long
)
```

### 8.2 서비스 계약
```kotlin
interface VoiceDetectionEngine {
    suspend fun start(config: DetectionConfig)
    suspend fun stop()
    fun updateKeywords(keywords: List<String>)
    val wakeEvents: Flow<WakeDetected>
    val stopEvents: Flow<StopDetected>
    val errors: Flow<DetectionError>
}

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

호출 계약(동시성/순서):
- `start()`/`stop()`/`updateKeywords()`는 단일 직렬 executor에서 실행되어야 한다.
- `start()`는 idempotent여야 하며 이미 실행 중이면 no-op으로 성공 반환한다.
- `stop()`은 항상 안전하게 호출 가능해야 하며 미실행 상태에서도 no-op 처리한다.
- `updateKeywords()`는 엔진 실행 중에도 원자적으로 반영되어야 하며 반영 중 감지는 이전 스냅샷 유지 후 스왑한다.
- 상충 호출(`stop()`과 `start()` 경쟁) 시 `stop` 우선 정책을 적용한다.

### 8.3 설정 모델
```kotlin
data class UserSettings(
    val stopPhrases: List<String>,
    val forcePlaybackEnabled: Boolean,
    val twoStepTriggerEnabled: Boolean,
    val confirmPhrase: String,
    val batterySaverEnabled: Boolean,
    val batteryThresholdPercent: Int = 20
)
```

## 9. 데이터 스키마 명세

### 9.1 Room 테이블
```sql
CREATE TABLE keyword_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phrase TEXT NOT NULL UNIQUE,
  normalized_phrase TEXT NOT NULL UNIQUE,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE find_sessions (
  id TEXT PRIMARY KEY,
  triggered_keyword TEXT NOT NULL,
  trigger_confidence REAL,
  started_at_epoch_ms INTEGER NOT NULL,
  ended_at_epoch_ms INTEGER,
  stop_reason TEXT,
  stage_reached INTEGER NOT NULL,
  force_playback_applied INTEGER NOT NULL,
  battery_saver_active INTEGER NOT NULL,
  detection_latency_ms INTEGER,
  stop_latency_ms INTEGER,
  created_at_epoch_ms INTEGER NOT NULL
);
```

### 9.2 DataStore 스키마 (단일 키)
- `force_playback_enabled`: Boolean
- `two_step_trigger_enabled`: Boolean
- `confirm_phrase`: String
- `battery_saver_enabled`: Boolean
- `battery_threshold_percent`: Int
- `stop_phrases`: StringSet

저장 보안 정책:
- DataStore는 Encrypted DataStore 단일 정책을 사용한다.
- `keyword_entries.phrase`, `keyword_entries.normalized_phrase`, `stop_phrases`는 저장 전 AES-GCM 필드 암호화 후 저장한다.
- 암호화 키는 Android Keystore에 보관하고 앱 재설치 시 키 재생성 정책을 적용한다.

### 9.3 데이터 보존
- `find_sessions` 30일 TTL
- 앱 시작 시 만료 레코드 정리
- 사용자 요청 시 즉시 전체 삭제

## 10. 권한/정책 명세
- `RECORD_AUDIO`: 리스닝 필수
- `FOREGROUND_SERVICE`: 백그라운드 감지 유지
- `FOREGROUND_SERVICE_MICROPHONE` (Android 14+): 마이크 유형 포그라운드 서비스
- `POST_NOTIFICATIONS` (Android 13+): 상태 알림
- `VIBRATE`: Stage3 진동
- `WAKE_LOCK` (필요 시): 짧은 처리 구간 안정화

권한 거부 시 동작:
- 마이크 거부: 리스닝 기능 전체 비활성
- 알림 거부: 서비스 동작은 가능하나 사용자 안내 강화
- 진동 거부: 사운드만 동작

포그라운드 서비스 규칙:
- 서비스 선언 시 `foregroundServiceType=\"microphone\"` 사용
- Android 14+에서는 사용자 가시 상태/허용된 시작 경로에서만 FGS 시작
- 백그라운드 시작 제한 충돌 시 사용자 액션 기반 재시작 유도
- Android 14+ `BOOT_COMPLETED`에서는 리스닝 자동 시작 금지, `READY_TO_RESUME` 표시 후 사용자 액션으로만 재개

## 11. 오류 코드 체계
- `E-AUD-001`: 마이크 권한 없음
- `E-AUD-002`: 마이크 점유 충돌
- `E-DET-001`: 감지 엔진 초기화 실패
- `E-DET-002`: 키워드 모델 로드 실패
- `E-PLY-001`: 오디오 포커스 획득 실패
- `E-PLY-002`: 재생 리소스 로드 실패
- `E-SET-001`: 잘못된 키워드 입력
- `E-DB-001`: 설정 저장 실패

오류는 UI 사용자 메시지 + 내부 로그 코드로 분리 관리한다.

## 12. 추적성 매트릭스 (PRD -> SPEC)
- PRD FR-01 -> SPEC 6.1
- PRD FR-02 -> SPEC 6.2
- PRD FR-03 -> SPEC 6.3
- PRD FR-04 -> SPEC 6.4
- PRD FR-05 -> SPEC 6.5
- PRD FR-06 -> SPEC 6.6
- PRD FR-07 -> SPEC 6.7
- PRD FR-08 -> SPEC 6.8

## 13. 테스트 명세

### 13.1 기능 테스트
- TC-FR01-01: 비행기 모드 키워드 감지
- TC-FR02-01: 키워드 3개 등록/수정/삭제
- TC-FR03-01: 감지 후 1.5초 내 재생 시작
- TC-FR04-01: 3단계 전환/종료 타이밍 검증
- TC-FR05-01: 중지 명령 1초 내 반응
- TC-FR06-01: 무음 상태 강제 재생 ON/OFF 비교
- TC-FR07-01: 2단계 트리거 ON/OFF 동작 비교
- TC-FR08-01: 배터리 20% 이하 자동 전환
- TC-PLT-01: Android 14+ 부팅 후 자동 리스닝 차단 및 사용자 액션 재개
- TC-PLT-02: 마이크 권한 런타임 철회 후 `LISTENING -> IDLE/ERROR` 전이 확인
- TC-ENG-01: `start/stop/updateKeywords` 경쟁 호출 시 동시성 계약 준수
- TC-REC-01: 엔진 오류 3회 후 서킷 오픈 및 사용자 경고 노출

### 13.2 비기능 테스트
- 12시간 배터리 소모 측정
- 24시간 리스닝 안정성(서비스 생존율)
- 소음 환경(TV/대화) 오탐률
- 저사양 단말 CPU/메모리 상한
- 프로세스 데스 내구성: `TRIGGERED` 중 비정상 종료 후 볼륨/진동 복구 검증
- 보안 테스트: 저장소 덤프에서 키워드 평문 비노출 검증

### 13.3 수용 기준 집계
- 핵심 AC 100% 통과
- P0/P1 결함 0건
- KPI 사전 측정치 목표 대비 90% 이상

## 14. 구현 우선순위
- P0: FR-01~FR-06
- P1: FR-07, FR-08
- P2: 접근성 옵션, 확장 중지 문구

## 15. 기본값 요약
- 웨이크 키워드 슬롯: 최대 3
- 중지 문구 기본: `멈춰`
- 2단계 확인 문구: `여기 있어`
- 2단계 입력 타임아웃: 4초
- 재트리거 쿨다운: 15초
- 보호 모드 진입/해제: 20% / 25%
- Stage3 최대 지속시간: 60초

## 16. 변경 관리
- 규격 변경 시 문서 버전 증가
- FR 영향 범위 명시 필수
- 데이터 스키마 변경은 마이그레이션 번호 동시 갱신
