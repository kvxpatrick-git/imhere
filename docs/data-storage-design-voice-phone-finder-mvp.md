# 데이터/저장소 설계서
## 음성 호출 기반 스마트폰 찾기 앱 Android MVP

## 1. 문서 정보
- 문서명: Data & Storage Design
- 버전: v1.0
- 작성일: 2026-02-15
- 기준 문서
- `docs/technical-spec-voice-phone-finder-mvp.md`
- `docs/technical-design-voice-phone-finder-mvp.md`

## 2. 저장소 원칙
1. 설정/정책 값은 Key-Value 저장소로 관리한다.
2. 세션 로그/키워드 엔트리는 구조화된 로컬 DB로 관리한다.
3. 민감 문자열(키워드/중지문구)은 저장 전 암호화한다.
4. 네트워크 전송 없이 로컬 저장만 수행한다.

## 3. 저장소 구성
- Room DB: `finder.db`
- SharedPreferences: `finder_legacy_prefs` (레거시 읽기 전용)
- Encrypted DataStore: `finder_settings.pb` (운영 기준)

역할 분리:
- Room: `keyword_entries`, `find_sessions`
- DataStore: 토글/정책/문구 집합
- SharedPreferences: 구버전 값 임시 호환 + 1회 마이그레이션 소스

## 4. Room 스키마

### 4.1 keyword_entries
```sql
CREATE TABLE keyword_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phrase TEXT NOT NULL UNIQUE,
  normalized_phrase TEXT NOT NULL UNIQUE,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);
```

컬럼 설명:
- `phrase`: 저장 계층에서 AES-GCM 암호화된 문자열(스키마 타입은 TEXT)
- `normalized_phrase`: 저장 계층에서 AES-GCM 암호화된 문자열(스키마 타입은 TEXT)

### 4.2 find_sessions
```sql
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

CREATE INDEX idx_sessions_started_at ON find_sessions(started_at_epoch_ms DESC);
CREATE INDEX idx_sessions_created_at ON find_sessions(created_at_epoch_ms DESC);
```

제약:
- `stage_reached`는 1~3
- `stop_reason`은 `StopReason` enum 문자열

## 5. DataStore 스키마
파일: `finder_settings.pb`

| key | type | default | 설명 |
|---|---|---|---|
| force_playback_enabled | Boolean | true | 무음/진동 강제재생 |
| two_step_trigger_enabled | Boolean | false | 2단계 트리거 |
| battery_saver_enabled | Boolean | true | 배터리 보호 사용 |
| battery_threshold_percent | Int | 20 | 보호모드 진입 임계값 |
| confirm_phrase | String | \"여기 있어\" | 확인 문구 |
| stop_phrases | StringSet | [\"멈춰\"] | 중지문구 목록 |
| should_listen | Boolean | false | 마지막 의도 상태 |
| last_engine_profile | String | BALANCED | 마지막 엔진 프로파일 |
| schema_version | Int | 1 | 설정 스키마 버전 |

직렬화 규칙:
- `stop_phrases`는 StringSet의 각 항목을 AES-GCM 암호화 문자열로 저장
- 키는 Android Keystore alias `finder-master-key`

## 6. SharedPreferences (레거시)
파일: `finder_legacy_prefs`

읽기 대상 키:
- `forcePlaybackEnabled` (Boolean)
- `twoStepTriggerEnabled` (Boolean)
- `batterySaverEnabled` (Boolean)
- `batteryThresholdPercent` (Int)
- `confirmPhrase` (String)
- `stopPhrases` (String, CSV)

정책:
- 앱 최초 실행 또는 버전 업 시 1회 읽기
- DataStore 마이그레이션 완료 후 `migrated_v1=true` 기록
- 이후 SharedPreferences는 읽기/쓰기 모두 금지

## 7. 저장 구조/흐름

### 7.1 키워드 저장
1. 입력 문자열 정규화
2. 앱 메모리에서 정규화 문자열 중복 검사
3. AES-GCM 암호화(저장값은 `phrase`, `normalized_phrase` 컬럼에 기록)
4. Room `keyword_entries` upsert
5. `updated_at_epoch_ms` 갱신

### 7.2 설정 저장
1. SettingsRepository.update 호출
2. DataStore transaction write
3. settingsFlow 발행
4. SettingsSyncUseCase가 엔진 config 반영

### 7.3 세션 로그 저장
1. 세션 시작 시 create
2. 단계 전환마다 `stage_reached` 업데이트
3. 종료 시 `stop_reason`, `ended_at_epoch_ms`, 지연값 업데이트
4. 만료 정리 워커에서 TTL 삭제

## 8. 데이터 보존/삭제 정책
- 세션 로그 TTL: 30일
- 앱 시작 시 `purgeExpired(now)` 실행
- 사용자 `전체 로그 삭제` 즉시 실행
- 앱 삭제 시 OS 정책에 따라 로컬 데이터 제거

## 9. 마이그레이션 정책

### 9.1 DB 마이그레이션
- Room `schemaVersion` 증가 시 SQL migration 필수
- destructive migration 금지
- 역직렬화/복호화 실패 시 해당 레코드 quarantine 후 계속 진행

### 9.2 SharedPreferences -> DataStore
- 앱 시작 시 `migrated_v1` 확인
- false면 레거시 값 읽어 DataStore에 변환 저장
- 성공 시 `migrated_v1=true` 저장 후 레거시 값 삭제
- 실패 시 재시도 1회, 이후 기본값 fallback + 오류코드 `E-DB-002`

### 9.3 암호화 키 로테이션
- 키 alias 버전: `finder-master-key-v1`
- 로테이션 시 `v2` 키 생성
- read-old/write-new 전략으로 백그라운드 재암호화
- 완료 시 `key_version=v2` 표시

## 10. 무결성/동시성 정책
- Room write는 IO dispatcher + transaction
- DataStore update는 atomic transform 블록 사용
- 키워드 변경과 엔진 반영 간 이벤트 순서 보장(`settingsFlow` monotonic)
- 충돌 발생 시 last-write-wins, 단 timestamp 기록

## 11. 장애/복구 정책
- DB open 실패: 앱 기능 제한 모드 + 재초기화 시도
- 암호화 실패: 민감값 접근 차단 + 사용자 재설정 유도
- 저장 실패는 에러코드와 함께 UI에 축약 메시지 표시

오류코드 추가:
- `E-DB-002`: 레거시 마이그레이션 실패
- `E-DB-003`: 데이터 복호화 실패
- `E-SEC-001`: Keystore 키 접근 실패

## 12. 검증 계획
기능 검증:
- 키워드 등록/수정/삭제 후 DB 반영
- 설정 토글 변경 후 DataStore 반영
- 세션 생성/종료 로그 정합성

마이그레이션 검증:
- SharedPreferences 샘플 데이터 변환 성공
- 변환 후 재실행 시 중복 마이그레이션 미실행

보안 검증:
- DB/DataStore 덤프에 키워드 평문 미노출
- 키 손실 시 오류 처리 동작 검증

성능 검증:
- 설정 저장 p95 < 100ms
- 세션 로그 insert p95 < 30ms

## 13. 운영 체크리스트
- [ ] Room schema export 확인
- [ ] Migration test 통과
- [ ] TTL worker 실행 확인
- [ ] 레거시 prefs 삭제 확인
- [ ] 암호화/복호화 실패 로그 코드 확인
