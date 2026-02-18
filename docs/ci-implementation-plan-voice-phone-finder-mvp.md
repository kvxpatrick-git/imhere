# CI 구현 계획서 (Deep Design)
## 음성 호출 기반 스마트폰 찾기 앱 Android MVP

## 1. 문서 정보
- 문서명: CI Implementation Plan
- 버전: v1.0
- 작성일: 2026-02-15
- 기준 문서
- `docs/technical-design-voice-phone-finder-mvp.md`
- `docs/technical-spec-voice-phone-finder-mvp.md`
- `docs/data-storage-design-voice-phone-finder-mvp.md`
- `docs/api-interface-spec-voice-phone-finder-mvp.md`

## 2. 목표 및 성공 기준
### 2.1 목표
1. 아키텍처 규칙 위반을 PR 단계에서 차단한다.
2. DB 스키마/마이그레이션 회귀를 머지 전 차단한다.
3. 문서-설계 정합성 붕괴를 자동 탐지한다.
4. Android 빌드/테스트를 일관된 환경에서 재현 가능하게 만든다.

### 2.2 성공 기준 (Go/No-Go Gate)
1. `main` 브랜치 머지 시 필수 CI job 100% 통과.
2. 아키텍처 위반 PR 탐지율 100%.
3. Room migration 테스트 누락 PR 차단율 100%.
4. flaky test 비율(7일 평균) 2% 이하.
5. CI 평균 시간(PR 기준) 15분 이내, p95 25분 이내.

## 3. CI 아키텍처 개요
CI는 3계층 게이트로 구성한다.

1. Fast Gate (3~5분)
- 코드 포맷/정적 규칙/문서 정합성/모듈 의존성 규칙
- 실패 시 즉시 종료

2. Build & Unit Gate (6~10분)
- 모듈 컴파일, 단위 테스트, 아키텍처 테스트

3. Reliability Gate (5~15분)
- Room migration 테스트
- 통합 테스트(모의 엔진)
- 보안/문서 정책 검사

브랜치 정책:
- `main`: 모든 필수 게이트 통과 필수
- `release/*`: 모든 게이트 + 서명/배포 준비 검증
- `feature/*`: Fast Gate 필수, 나머지는 조건부

## 4. 파이프라인 설계 (GitHub Actions 기준)

### 4.1 Workflow 파일 구조
- `.github/workflows/ci-pr.yml`
- `.github/workflows/ci-main.yml`
- `.github/workflows/ci-nightly.yml`
- `.github/workflows/release-check.yml`

### 4.2 공통 실행 환경
- Runner: `ubuntu-latest`
- JDK: 17
- Android SDK: API 34 + build-tools 최신 안정
- Gradle cache: wrapper + dependencies + build cache
- Kotlin daemon cache 활성

### 4.3 트리거 규칙
- `ci-pr.yml`: `pull_request` (opened, synchronize, reopened)
- `ci-main.yml`: `push` on `main`
- `ci-nightly.yml`: daily cron (KST 02:00)
- `release-check.yml`: `workflow_dispatch` + `push` on `release/*`

## 5. Job 상세 설계

### 5.1 Job A: Preflight
목적:
- 환경 점검 및 병렬 fan-out 준비

입력:
- git diff, changed files

출력:
- 변경 범위 태그(`docs-only`, `android-code`, `db-schema`)

실패 기준:
- gradle wrapper 불일치
- 필수 환경 변수 누락

### 5.2 Job B: Lint & Static Rules (Fast Gate)
실행:
- `./gradlew ktlintCheck detekt lintDebug`

실패 기준:
- 스타일/정적 분석 위반 1건 이상

아티팩트:
- detekt SARIF
- lint HTML report

### 5.3 Job C: Architecture Rules Gate
목적:
- Clean Architecture 의존성 강제

실행:
- `./gradlew :core-domain:test --tests "*Architecture*"`
- `./gradlew :build-logic:checkArchitecture` (custom task)

검사 규칙:
1. `core-domain` -> `core-data/platform-*/feature-*/app` import 금지
2. `feature-*` -> `platform-*` 직접 의존 금지
3. `core-domain`에서 `android.*`, `androidx.*`, `Room`, `DataStore`, `Compose` 금지

실패 기준:
- 규칙 위반 1건 이상

아티팩트:
- 위반 dependency graph (`architecture-violations.json`)

### 5.4 Job D: Build & Unit Test Gate
실행:
- `./gradlew assembleDebug testDebugUnitTest`

실패 기준:
- 빌드 실패
- 단위 테스트 실패

성능 목표:
- 10분 이내 완료

### 5.5 Job E: DB Schema & Migration Gate
목적:
- Room 스키마 회귀 방지

실행:
- `./gradlew :core-data:assembleDebug :core-data:testDebugUnitTest --tests "*Migration*"`
- `./gradlew :core-data:exportRoomSchema`
- git diff로 schema JSON 변경 탐지

규칙:
1. 엔티티 변경 시 schema export 파일 동반 커밋 필수
2. `schemaVersion` 증가 시 migration 테스트 클래스 필수
3. destructive migration 플래그 금지

실패 기준:
- 스키마 변경인데 export 누락
- migration 테스트 누락
- destructive migration 감지

아티팩트:
- `core-data/schemas/**`
- migration report

### 5.6 Job F: Security & Privacy Gate
목적:
- 민감정보 평문 저장/로그 노출 방지

실행:
- secret scan (gitleaks/trufflehog)
- custom grep rules for forbidden patterns
- policy linter for privacy 문서/코드 키워드

금지 패턴 예:
- `Log.*(keyword|phrase)`
- `triggered_keyword` 평문 로그 출력
- debug builds에서 PII 출력

실패 기준:
- high severity 보안 이슈 1건 이상

### 5.7 Job G: Documentation Consistency Gate
목적:
- 설계 문서 간 핵심 계약 정합성 확인

검사 대상:
- 인터페이스 키워드: `VoiceDetectionEngine.updateKeywords`, `AlertPlayer.playSequence`
- DB 키: `triggered_keyword`, `confirm_phrase`, `stop_phrases`
- 정책 키워드: `READY_TO_RESUME`, `FOREGROUND_SERVICE_MICROPHONE`

실행:
- `scripts/ci/check_docs_consistency.sh`

실패 기준:
- 기준 문서 2개 이상 간 핵심 계약 문자열 불일치

### 5.8 Job H: Integration Smoke (Nightly/Main)
목적:
- 오케스트레이터 + 저장소 + 정책 연동 최소 보장

실행:
- 모의 엔진 기반 integration tests
- 상태 전이 시나리오 10개

실패 기준:
- 핵심 시나리오 실패 1건 이상

### 5.9 Job I: Runtime Smoke (Main/Nightly/Release)
목적:
- 앱 실행 직후 크래시/ANR 회귀를 병합 전 조기 탐지

실행:
- Android Emulator(API 34) 부팅
- `./gradlew :app:assembleDebug`
- `scripts/ci/check_android_runtime_smoke.sh --require-device`

검사 규칙:
1. `FATAL EXCEPTION` 로그 존재 시 실패
2. `ANR in com.imhere.app` 로그 존재 시 실패
3. 앱 프로세스의 치명적 예외 패턴(`Process: com.imhere.app ... Exception/Error`, `SIGSEGV`) 존재 시 실패

실패 기준:
- 크래시/ANR 패턴 1건 이상

## 6. Required Checks 정책
PR merge required checks:
1. `preflight`
2. `docs_consistency`
3. `lint_and_static`
4. `architecture_rules`
5. `build_and_unit`
6. `db_migration`
7. `security_privacy`

`Integration Smoke`는 `main`/`nightly` 필수.
`Runtime Smoke`는 `main`/`nightly`/`release` 필수.

## 7. 아키텍처 규칙 구현 상세

### 7.1 ArchUnit 테스트 예시
- package rule:
- `..core.domain..` should not depend on `..core.data..`
- `..feature..` should not depend on `..platform..`

### 7.2 커스텀 Gradle Task
태스크명:
- `checkArchitecture`
- `checkForbiddenImports`

입력:
- 소스 파일 AST/import graph

출력:
- `build/reports/architecture/violations.json`

실패 규칙:
- 위반 건수 > 0 -> non-zero exit

### 7.3 확장성
- 모듈 추가 시 화이트리스트 기반으로 의존성 매트릭스 자동 업데이트
- 룰 변경은 `build-logic` 단일 소스에서 관리

## 8. DB 마이그레이션 품질게이트 상세

### 8.1 스키마 버전 정책
- `schemaVersion` 증가 시:
1. migration SQL/Room Migration 객체
2. forward migration test
3. (권장) backward compatibility read test

### 8.2 테스트 데이터셋
- v1 baseline DB fixture
- corrupted row fixture
- encrypted field fixture

### 8.3 실패 처리
- migration 실패 시 PR 차단
- schema export 누락 시 명시적 오류 메시지 출력

## 9. 보안/개인정보 게이트 상세

### 9.1 민감값 룰
- 키워드, 중지문구, 발화 원문 로그 금지
- 암호화 필드 저장 전 평문 persistence 금지

### 9.2 정적 규칙
- 금지 API 사용 탐지 규칙
- debug-only logging guard 확인

### 9.3 예외 승인 프로세스
- 일시적 예외는 `security-exceptions.yaml`에 만료일 포함
- 만료일 초과 시 CI 실패

## 10. 문서 정합성 게이트 상세

### 10.1 기준 문서
- 기술명세서 = Source of Truth
- 기술설계/데이터설계/API문서/권한문서는 정합성 대상

### 10.2 검사 항목
1. 인터페이스 시그니처 핵심 문자열
2. 데이터 스키마 키명
3. 권한/정책 키워드
4. 오류코드 표준 Prefix

### 10.3 구현 방법
- Bash + `rg` 기반 최소 구현 후,
- 추후 파서 기반(kt + markdown parser)으로 고도화

## 11. 성능/안정성 운영 설계

### 11.1 시간 예산
- PR Fast Gate: 5분 이하
- PR Full Gate: 15분 이하
- Nightly Full: 30분 이하

### 11.2 캐시 전략
- Gradle remote cache 사용
- Android SDK cache
- test result cache 분리

### 11.3 flaky test 운영
- flaky quarantine tag
- 3회 재시도 정책은 Nightly에만 적용
- PR 필수 게이트는 재시도 없이 실패 처리

## 12. 브랜치/릴리즈 운영 정책

### 12.1 PR 정책
- squash merge only
- required checks mandatory
- code owners review 최소 1인

### 12.2 release 브랜치
- `release/*`에서 추가 게이트 실행
- versioning, changelog, signing config 검사

### 12.3 hotfix
- hotfix PR도 동일 필수 게이트 적용
- 단, nighty job 제외 가능

## 13. 단계별 도입 계획 (4주)

### Week 1
- CI skeleton 구축 (`ci-pr.yml`, `ci-main.yml`)
- Lint/Unit/Build gate 연결

### Week 2
- Architecture gate + 문서 정합성 스크립트 도입
- required checks 브랜치 보호 연결

### Week 3
- DB migration gate + schema export 강제
- Security/Privacy gate 도입

### Week 4
- Nightly integration smoke 구축
- flaky 운영 규칙 정착 + KPI 측정

## 14. 위험요소 및 대응
1. 빌드 시간 증가
- 대응: 변경영역 기반 selective job + 캐시 튜닝

2. 과도한 오탐으로 개발 속도 저하
- 대응: rule severity 단계화(warn -> fail)

3. 문서 정합성 스크립트 유지보수 부담
- 대응: 핵심 키 최소 집합부터 시작

4. Android CI 환경 불안정
- 대응: runner pinned image + retry policy 분리

## 15. 구현 산출물 체크리스트
- [x] `.github/workflows/ci-pr.yml`
- [x] `.github/workflows/ci-main.yml`
- [x] `.github/workflows/ci-nightly.yml`
- [x] `.github/workflows/release-check.yml`
- [x] `.github/workflows/branch-protection.yml`
- [x] `build-logic` 아키텍처 검사 태스크
- [x] `scripts/ci/check_docs_consistency.sh`
- [x] `scripts/ci/check_architecture_imports.sh`
- [x] `scripts/ci/check_room_schema.sh`
- [x] `scripts/ci/check_android_runtime_smoke.sh`
- [x] `scripts/ci/apply_branch_protection.sh`
- [x] Room schema export 설정
- [x] migration 테스트 템플릿
- [x] security scanning 설정
- [x] branch protection rule 적용

## 16. 승인 기준
아래 조건 충족 시 CI 아키텍처 구축 완료로 본다.
1. 필수 required checks가 PR에서 모두 강제됨
2. 의존성 규칙 위반 PR이 실제로 실패함
3. migration 누락 PR이 실제로 실패함
4. 문서 정합성 위반 PR이 실제로 실패함
5. main/nightly 파이프라인 안정 실행 7일 연속 성공률 95% 이상
