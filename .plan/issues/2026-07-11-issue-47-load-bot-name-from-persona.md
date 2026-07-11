# 2026-07-11 — 하드코딩된 봇 이름을 페르소나 조회로 교체

- Date: 2026-07-11
- GitHub Issue: #47
- Status: Draft

## Goal

양수 user 이름은 account server에서 계속 조회하고, 음수 user 이름은 shared DB의 `bot_personas.name`에서 user_id 기준으로 조회한다.

## Non-goals

- Account server에 bot member 생성
- Persona/schema 또는 Admin 관리 UI 구현

## Context / Constraints

- `AccountClient`는 `memberId <= 0`이면 `BotMemberMaker`로 우회한다.
- `BotMemberMaker`는 -1~-4 이름과 랜덤 -1/-2 ID를 하드코딩한다.
- database #8 schema가 선행되어야 한다.

## Approach (Checklist)

- [ ] **Step 0: Recon** (`AccountClient`, `BotMemberMaker`, `GameMatchService`, admin match flow와 reactive DB patterns 확인)
- [ ] **Step 1: Persistence** (음수 `user_id`로 enabled persona 이름을 조회하는 reactive repository 추가)
- [ ] **Step 2: Service** (positive→account, negative→persona 분기; 하드코딩 제거; missing persona 오류/fallback 명시)
- [ ] **Step 3: Matching** (랜덤 하드코딩 ID 대신 활성 bot candidate 조회 필요 여부 반영)
- [ ] **Step 4: Tests** (positive/negative/unknown/disabled persona와 account 호출 여부 검증)
- [ ] **Step 5: Rollout / Rollback** (database #8 선행, lobby app rollback)

## Validation

- **Commands to run:** `./gradlew test`; `./gradlew compileJava compileKotlin`
- **Expected output:** 음수 ID의 표시 이름이 persona DB 값과 일치하고 양수 ID 흐름은 변하지 않음

## Risks & Rollback

- **Risks:** persona 누락 시 room DTO 생성 실패; blocking DB 접근 혼입; random bot selection과 enabled persona catalog 불일치
- **Rollback steps:** Lobby 이전 버전 배포. Database schema 유지.

## Open Questions

- persona 누락 시 `bot` fallback보다 명시적 오류가 적합한가? 운영 가시성을 위해 오류 + 로그 권장.
- 랜덤 bot 선택 책임을 lobby가 가질지 game/admin이 제공한 ID만 사용할지 결정 필요.
