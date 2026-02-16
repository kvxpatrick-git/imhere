# 기술 설계서 (Technical Design)
## 음성 호출 기반 스마트폰 찾기 앱 Android MVP

## 1. 문서 메타데이터
- 문서명: Technical Design Document (TDD)
- 문서 버전: v1.0
- 작성일: 2026-02-15
- 기준 문서
- PRD: `docs/prd-voice-phone-finder-mvp.md`
- 기술명세서: `docs/technical-spec-voice-phone-finder-mvp.md`
- CI 구현계획: `docs/ci-implementation-plan-voice-phone-finder-mvp.md`

## 2. 설계 목표
1. 완전 오프라인 동작
2. 감지 지연 P95 1.5초 이하
3. 12시간 백그라운드 배터리 소모 6% 이하 목표
4. 백그라운드 제약 환경에서도 리스닝 지속 가능성 확보
5. 향후 엔진 교체 가능한 모듈 구조 확보

## 3. 아키텍처 개요
아키텍처는 Clean Architecture 원칙(의존성 역전)을 따르는 계층형 + 이벤트 기반 상태머신으로 구성한다.

- UI Layer: Compose 화면, ViewModel, 상태 렌더링
- Domain Layer: 유스케이스, 정책 판단, 상태머신
- Data Layer: 설정 저장소, 세션 로그 저장소
- Platform Layer: 음성 감지 엔진, 오디오/진동 제어, 배터리/권한 모니터

핵심 원칙:
- 음성 엔진과 재생 엔진은 인터페이스로 추상화
- 상태 전이는 단일 오케스트레이터(`FinderOrchestrator`)에서만 수행
- 동시성은 Kotlin Coroutines + Flow 사용
- 의존 방향은 바깥 -> 안쪽이며, 구현체 바인딩은 DI에서 수행한다.
- Domain은 Android/DB/UI 타입을 참조하지 않는다.

### 3.1 의존성 규칙 (정석)
- `UI`는 `Domain`의 UseCase/StatePort만 의존
- `App/Composition Root`는 `Data`/`Platform` 구현체를 `Domain` 포트에 주입
- `Data`는 `Domain`의 Repository Port를 구현
- `Platform`은 `Domain`의 Engine Port를 구현
- `Domain`은 어떤 외부 구현체도 직접 참조하지 않음

## 4. 모듈 구조

### 4.1 Gradle 모듈
- `app`: 앱 엔트리, DI 조립, 내비게이션
- `core-model`: 공통 모델/enum/error
- `core-domain`: 유스케이스, 상태머신, 정책
- `core-data`: Room, DataStore, Repository 구현
- `feature-listening`: 리스닝/세션 오케스트레이션
- `feature-settings`: 키워드 및 정책 설정 UI/로직
- `platform-audio`: AudioManager, MediaPlayer/ExoPlayer, Vibrator
- `platform-voice`: 온디바이스 인식 엔진 어댑터
- `platform-monitoring`: 배터리/권한/서비스 생명주기 모니터

### 4.2 패키지 가이드
- Domain은 Android Framework 의존 금지
- Platform은 Android API 직접 사용 가능
- UI는 Domain 인터페이스만 참조

### 4.3 모듈 의존 매트릭스
- 허용
- `app -> feature-*, core-domain, core-data, platform-*`
- `feature-* -> core-domain, core-model`
- `core-data -> core-domain, core-model`
- `platform-* -> core-domain, core-model`
- `core-domain -> core-model`
- 금지
- `core-domain -> core-data`, `core-domain -> platform-*`, `core-domain -> feature-*`, `core-domain -> app`

### 4.4 아키텍처 준수 자동 검증
- CI에서 모듈 의존 규칙 위반 시 빌드 실패 처리
- 정적 분석 규칙
- `core-domain` 패키지에서 `android.*`, `androidx.*`, `Room`, `DataStore`, `Compose` import 금지
- `feature-*`에서 `platform-*` 직접 의존 금지
- ArchUnit/커스텀 Gradle 스크립트로 의존 그래프 검사

## 5. 핵심 컴포넌트 설계

### 5.1 FinderOrchestrator
역할:
- 시스템 단일 상태 소유자
- 음성 이벤트 수신 후 트리거 판정
- AlertPlayer 호출 및 종료 처리
- 세션 로그 기록

입력:
- `WakeDetected`, `StopDetected`, `BatteryStateChanged`, `PermissionStateChanged`

출력:
- `DomainState`, `DomainEvent`, `SessionLog`

동시성 모델:
- 단일 CoroutineScope + Mutex 기반 상태 전이 직렬화
- 중복 트리거 방지를 위해 세션 단위 락 적용

### 5.2 VoiceDetectionEngineAdapter
역할:
- 온디바이스 음성 모델 로딩
- 웨이크/중지/확인 문구 감지 이벤트 생성

설계 결정:
- 엔진 추상화 인터페이스를 유지하고 MVP에서는 `VoskAdapter`를 기본 구현으로 채택
- 인식 문법은 제한 어휘(키워드, 중지어, 확인문구)로 축소하여 CPU/오탐 절감

입력 오디오 사양:
- Mono PCM 16kHz, 16bit
- 프레임 길이 20ms

### 5.3 AlertPlayerImpl
역할:
- 3단계 사운드 시퀀스 실행
- 볼륨 임시 조정/복원
- 진동 패턴 실행/중지

핵심 로직:
- Stage 타이머 기반 상태 전환
- 중지 이벤트 수신 시 즉시 취소
- 세션 종료 시 자원 정리 보장(`finally` 블록)

### 5.4 SettingsRepository
역할:
- 키워드 및 정책 저장/조회
- 변경 이벤트 스트림 발행(`SettingsChanged`)

저장소:
- 정형 구조: Room (`keyword_entries`, `find_sessions`)
- 단순 토글/문구: DataStore Preferences (`force_playback`, `two_step`, `confirm_phrase`, `stop_phrases` 등)

### 5.5 SettingsSyncUseCase (Application Layer)
역할:
- `SettingsRepository.settingsFlow` 구독
- 변경 시 `VoiceDetectionEngine.updateKeywords()` 및 탐지 설정 반영
- 저장소와 플랫폼 간 직접 결합을 제거

### 5.6 BatteryPolicyController
역할:
- 배터리 상태 브로드캐스트 구독
- 보호 모드 진입/해제 판정 (20/25 히스테리시스)
- 감지 엔진 프로파일 변경 이벤트 발행

## 6. 상태머신 설계

### 6.1 상태 정의
- `IDLE`
- `LISTENING`
- `PENDING_CONFIRMATION`
- `TRIGGERED(stage=1|2|3)`
- `STOPPING`
- `STOPPED`
- `ERROR(recoverable|fatal)`

보조 플래그:
- `batterySaverActive`는 상태가 아닌 보조 플래그

### 6.2 전이 규칙
1. `IDLE -> LISTENING`
- 조건: 권한 충족 + 사용자 시작
- 액션: 음성 엔진 start, 포그라운드 서비스 시작

2. `LISTENING -> TRIGGERED(stage=1)`
- 조건: 웨이크 감지 + 2단계 트리거 OFF
- 액션: 세션 생성, Stage1 시작

3. `LISTENING -> PENDING_CONFIRMATION`
- 조건: 웨이크 감지 + 2단계 트리거 ON
- 액션: 4초 확인 타이머 시작

4. `PENDING_CONFIRMATION -> TRIGGERED(stage=1)`
- 조건: 확인 문구 수신(4초 이내)
- 액션: 세션 생성, Stage1 시작

5. `PENDING_CONFIRMATION -> LISTENING`
- 조건: 4초 타임아웃 또는 인식 실패

6. `TRIGGERED(stage=n) -> TRIGGERED(stage=n+1)`
- 조건: 현재 단계 타이머 종료

7. `TRIGGERED -> STOPPING`
- 조건: 중지 키워드 감지 또는 타임아웃

8. `STOPPING -> STOPPED`
- 액션: 오디오 복원, 로그 저장, 엔진 재대기

9. `STOPPED -> LISTENING`
- 액션: 엔진 재대기

10. `ANY -> ERROR`
- 조건: 엔진 실패/치명 오류
- 복구 가능 오류는 재시도 후 `LISTENING` 복귀

## 7. 시퀀스 설계

### 7.1 기본 찾기 시퀀스
1. 사용자 발화 -> 오디오 캡처
2. VoiceDetectionEngine이 웨이크 키워드 이벤트 발행
3. Orchestrator가 트리거 정책 판정
4. AlertPlayer Stage1 즉시 재생
5. 타이머로 Stage2, Stage3 전환
6. 사용자 `멈춰` 발화 -> StopDetected 이벤트
7. AlertPlayer stop + 볼륨 복원 + 로그 저장
8. `LISTENING` 복귀

### 7.2 2단계 트리거 시퀀스
1. 웨이크 이벤트 수신
2. `PENDING_CONFIRMATION` 상태로 4초 타이머 시작
3. 확인 문구 인식 시 트리거 확정
4. 4초 초과 시 폐기 후 LISTENING 유지

### 7.3 배터리 보호 모드 시퀀스
1. 배터리 <=20% 이벤트
2. BatteryPolicyController가 보호 모드 활성 이벤트 발행
3. DetectionConfig를 `power_save`로 변경
4. UI/알림에 보호 모드 표시
5. 배터리 >=25% 시 해제

## 8. 데이터 흐름 설계

### 8.1 설정 변경 흐름
- UI -> SettingsViewModel -> UpdateSettingsUseCase -> SettingsRepository
- 저장 성공 -> `settingsFlow` 갱신
- SettingsSyncUseCase가 갱신 구독 후 엔진 갱신

### 8.2 세션 로그 흐름
- 세션 시작 시 `find_sessions` 초안 insert
- 단계 전환 시 stage 업데이트
- 종료 시 stop_reason, latency, ended_at 업데이트

### 8.3 정리 작업
- 앱 시작/매일 1회 WorkManager 실행
- 30일 초과 로그 삭제

## 9. 성능 설계

### 9.1 감지 지연 단축 전략
- 키워드 문법 제한(폐쇄형 vocabulary)
- 오디오 파이프라인 고정 샘플레이트 사용
- 인식 결과 디바운스 최소화 (150ms)
- 웨이크 이벤트와 재생 시작 사이 경로 최소화

### 9.2 배터리 최적화 전략
- 화면 OFF 시 인식 파이프라인 thread priority 조정
- 보호 모드 시 프레임 처리 간격 완화
- 불필요 로그 샘플링 차단
- 엔진 warm start 유지로 재초기화 비용 축소

### 9.3 메모리 목표
- 상시 메모리 점유 180MB 이하(중급 단말 기준)
- OOM 방지: 오디오 버퍼 풀 재사용

## 10. 안정성/복구 설계

### 10.1 서비스 생존성
- ForegroundService + `START_STICKY`
- 서비스 재생성 시 마지막 설정 재적용
- 엔진 실패 시 백오프 재시도: 1s, 2s, 4s
- 프로세스 데스 복구 시 마지막 의도 상태(`shouldListen`)는 복원하되, Android 14+에서는 자동 마이크 리스닝을 즉시 시작하지 않는다.
- Android 14+에서는 `BOOT_COMPLETED` 이후 상태만 복원(`READY_TO_RESUME`)하고, 사용자 명시 액션(알림 탭/앱 진입/퀵액션)으로 리스닝을 재개한다.
- Android 13 이하에서도 정책 충돌 기기에서는 동일한 사용자 액션 재개 경로를 fallback으로 사용한다.

### 10.2 장애 격리
- 음성 엔진 오류가 UI 쓰레드에 전파되지 않도록 SupervisorJob 사용
- AlertPlayer 오류와 Detection 오류를 분리 처리

### 10.3 데이터 무결성
- 설정 쓰기 트랜잭션 보장
- 세션 로그는 시작/종료 레코드 원자적 갱신

### 10.4 오류 복구 정책 (정량)
- 재시도 가능한 오류(`E-DET-001`, `E-PLY-001`)는 지수 백오프 `1s, 2s, 4s` 후 최대 3회 재시도
- 3회 실패 시 5분 서킷 오픈(`DEGRADED`) 후 자동 재시도 중단
- 서킷 오픈 중에는 리스닝 비활성 및 사용자 가시 경고 노출
- 사용자 수동 재시도 시 서킷 즉시 half-open 전환
- 30분 내 서킷 오픈 3회 이상이면 `ERROR(fatal)` 전환 및 설정 진단 가이드 표시

## 11. 권한 및 OS 대응 설계

### 11.1 권한 플로우
1. 최초 실행 시 마이크 권한 요청
2. 거부 시 이유 설명 + 재요청 버튼
3. 영구 거부 시 설정 앱 이동 딥링크 제공

### 11.2 Android 버전 대응
- Android 13+: 알림 권한 별도 요청
- Android 12+: 마이크 사용 인디케이터 대응 안내
- Android 14+: `FOREGROUND_SERVICE_MICROPHONE` 권한 및 `foregroundServiceType=\"microphone\"` 선언
- OEM 백그라운드 제한 기기: 제조사별 가이드 문구 제공

### 11.3 시작 정책 (정석)
- 리스닝 시작 API는 `userInitiated=true` 컨텍스트를 기본 요구로 설계
- 백그라운드 자동 시작 요청은 정책 게이트에서 차단하고 `READY_TO_RESUME` 상태로 전환
- `READY_TO_RESUME` 상태에서는 고우선 알림 액션으로만 재개 허용

### 11.4 무음/방해금지 대응
- 강제 재생 ON 시 오디오 채널 우선순위 높은 채널 사용
- OS 정책으로 완전 우회 불가한 경우 사용자에게 안내

### 11.5 오디오 인터럽션 대응
- 통화 시작/진행 중: 세션 즉시 정지, 상태 `STOPPED`, 사유 `CALL_INTERRUPTION`
- 블루투스 라우팅 전환: 오디오 포커스 재협상 후 1회 재시도
- 오디오 포커스 상실(영구): 재생 중지 후 `LISTENING` 복귀
- 오디오 포커스 상실(일시): 2초 내 회복 시 재개, 초과 시 종료

## 12. 보안/개인정보 설계
- 음성 원본 저장 없음
- 이벤트 로그만 로컬 저장
- 민감 설정 저장은 Encrypted DataStore 단일 정책을 사용
- 키워드/중지문구는 저장 전 필드 단위 암호화(AES-GCM, Android Keystore 기반 키) 후 Room 저장
- 키워드 조회는 런타임 메모리에서만 복호화하고, 로그/크래시에 평문 기록 금지
- 앱 외부로 데이터 전송 없음 (MVP)

## 13. 관측성(Observability) 설계

### 13.1 내부 메트릭
- `wake_detect_latency_ms`
- `playback_start_latency_ms`
- `stop_latency_ms`
- `early_stop_ratio` (트리거 후 3초 내 중지 비율, 오탐 프록시)
- `manual_cancel_ratio` (앱 UI 중지 비율, 오탐/UX 프록시)
- `session_success_rate`
- `battery_drain_per_hour`

### 13.2 로깅 레벨
- DEBUG: 개발 빌드만 상세 로그
- INFO: 상태 전이/세션 시작 종료
- WARN/ERROR: 엔진 오류 코드

### 13.3 개인정보 최소화
- 로그에 사용자 발화 원문 저장 금지
- 키워드는 해시 또는 마스킹 표시

## 14. 테스트 설계

### 14.1 단위 테스트
- 상태머신 전이 규칙
- 2단계 트리거 타이머 로직
- 볼륨 복원 정책
- 배터리 히스테리시스

### 14.2 통합 테스트
- VoiceDetectionEngine mock -> Orchestrator -> AlertPlayer 연동
- Settings 변경 시 엔진 업데이트
- 서비스 재시작 복구 시나리오

### 14.3 디바이스 테스트
- 저/중/고 사양 단말 각 1대 이상
- 소음 환경별 오탐 측정
- 12시간 배터리 런
- Android 14+에서 `BOOT_COMPLETED` 후 자동 재개 차단 및 사용자 액션 재개 검증
- 권한 런타임 철회(마이크/알림) 후 상태 전이 및 복구 플로우 검증
- `TRIGGERED` 중 프로세스 데스 시 세션 정리/볼륨 복원/재시작 정책 검증

### 14.4 성능 테스트 기준
- 감지~재생 P95 <= 1.5s
- 중지~완전중단 P95 <= 1.0s
- 24시간 생존율 >= 99%

## 15. 배포 및 롤아웃 설계

### 15.1 릴리즈 전략
- 내부 알파 -> 클로즈드 베타 -> 프로덕션 단계적 확대
- 초기 5%, 20%, 50%, 100% 순차 롤아웃

### 15.2 롤백 기준
- P0 크래시 발생률 임계치 초과
- 감지 실패율 급증(베이스라인 대비 +30% 이상)
- 배터리 소모 지표 기준 미달

### 15.3 운영 체크리스트
- 권한 플로우 정상
- 포그라운드 서비스 알림 정상
- OEM 주요 기기(삼성/샤오미/오포) 백그라운드 유지 확인

## 16. 의사결정 기록 (ADR 요약)
1. ADR-001: 완전 오프라인 원칙 채택
- 이유: 프라이버시/신뢰성

2. ADR-002: 음성 엔진 추상화 계층 도입
- 이유: 엔진 교체/튜닝 유연성 확보
- 기본 구현: `VoskAdapter` (오프라인/로컬 추론)
- 제약: 모델 크기/로딩시간/라이선스 검토를 릴리즈 게이트로 관리
- 릴리즈 게이트
- 모델 파일 총합 60MB 이하
- Cold start(엔진 초기화) P95 2.5초 이하
- 웨이크 감지 정확도(사내 테스트 코퍼스) 92% 이상

3. ADR-003: 상태머신 중앙집중 오케스트레이션
- 이유: 동시성 버그와 중복 트리거 예방

4. ADR-004: 20/25 배터리 히스테리시스
- 이유: 모드 플래핑 방지

## 17. 구현 단계 계획
1. Sprint 1
- 프로젝트 모듈 골격
- ForegroundService + 상태머신 최소 구현
- 기본 키워드 1개 고정 감지

2. Sprint 2
- 사용자 키워드 1~3 관리
- 3단계 재생/중지 구현
- 로그 저장

3. Sprint 3
- 2단계 트리거
- 배터리 보호 모드
- 성능 최적화/테스트 자동화

4. Sprint 4
- 접근성/권한 UX 보완
- 베타 릴리즈 준비

## 18. 오픈 이슈
- 특정 OEM에서 마이크 백그라운드 제한 강도가 높을 수 있음
- 음성 엔진 모델 크기와 초기 로딩 시간의 트레이드오프 검증 필요
- DND 정책 우회 가능 범위는 기기별 편차 검증 필요

## 19. 부록: 기본 상수
```kotlin
object FinderDefaults {
    const val MAX_KEYWORDS = 3
    const val DETECTION_COOLDOWN_SEC = 15
    const val CONFIRM_WINDOW_SEC = 4
    const val BATTERY_SAVER_ENTER = 20
    const val BATTERY_SAVER_EXIT = 25
    const val STAGE1_SEC = 2
    const val STAGE2_SEC = 10
    const val STAGE3_MAX_SEC = 60
    const val WAKE_CONFIDENCE_BALANCED = 0.62f
    const val WAKE_CONFIDENCE_POWER_SAVE = 0.70f
}
```
