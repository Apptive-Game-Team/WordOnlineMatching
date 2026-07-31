# 2026-07-31 — 안정적이고 부하 낮은 matchmaking 재설계

- Date: 2026-07-31
- GitHub Issue: 없음 (구현 착수 시 lobby repo에 issue 생성)
- Owning repository: `lobby` (WordOnlineMatching)
- Status: Draft

## Goal

- 매칭 상태 조회를 Redis `GET` 1회로 만들어 lobby가 폴링 사용자 수에 비례해 game server / DB를 때리지 않게 한다.
- 매칭 tick을 "5초당 1쌍"에서 "tick당 배치"로 바꿔 queue 길이에 비례한 대기 폭증을 없앤다.
- lobby instance가 2개 이상이어도 같은 user가 두 session에 들어가지 않도록 단일 owner + 원자적 소비를 보장한다.
- 대기 시간에 비례해 MMR tolerance를 넓혀 starvation을 없앤다.
- session 생성 실패를 "매칭 실패 후 방치"가 아니라 requeue + 재시도로 처리한다.

## Acceptance Criteria

1. `GET /api/users/mine/status` 처리 중 outbound HTTP 0회, DB query 0회, Redis 호출 1회.
2. game server room list fan-out은 사용자 요청 경로에서 사라지고, 주기 reconciler에서만 발생 (사용자 수와 무관, game server 수 × 1/10초).
3. queue에 2N명이 있을 때 한 tick 안에 최대 N쌍이 매칭된다 (batch cap까지).
4. lobby 2 instance 동시 기동 + queue 100명 부하에서 동일 userId가 두 session에 배정되는 사례 0건.
5. `MATCH_MAX_WAIT` 도달한 user는 MMR과 무관하게 상대가 있으면 매칭된다.
6. `MMR_KEY` 등 보조 키가 만료 처리 후 남지 않는다 (현재 leak).
7. client 코드 변경 없이 동작한다 (endpoint·응답 shape 유지).

## Non-goals

- client push 전환(SSE/long-poll) — Phase 2. 지금은 폴링 유지하되 폴링 1회 비용을 O(1)로 만든다.
- MMR 계산·랭크 시스템 개편.
- game server → lobby session 종료 callback (game repo 변경) — Phase 3. 지금은 reconciler로 대체.
- party/친선전, region 분리.

## Context / Constraints — 현재 구조의 실제 문제

근거 파일: `matching/service/GameMatchService.kt`, `matching/repository/MatchingQueueRepository.kt`,
`auth/service/UserService.java`, `session/service/LegacyGameMatchService.java`,
`server/service/GameServerManagementService.java`, client `LobbyScene/MatchStatusPoller.cs`.

### 부하 (주범)

1. **status 폴링이 game server fan-out을 유발.** client는 2초마다 `/api/users/mine/status`
   (`MatchStatusPoller.cs:17`). 서버는 `UserService.getStatus()`에서 Redis 2회 →
   `gameSessionService.getAllGameSessions()` → `servers` DB SELECT + **모든 ACTIVE game server에
   HTTP 요청 + 전체 room list 수신** → user 포함 여부 스캔 (`UserService.java:119-133`).
   로비 접속자 100명이면 초당 약 50회 × (DB 1 + game server M) 호출. 사용자가 늘수록 선형 증가.
2. **`dequeueBestPair()`가 5초마다 queue 전체를 앱으로 끌어옴.** `ZRANGE 0 -1` + `HMGET` 전량
   (`MatchingQueueRepository.kt:32-50`). queue가 커질수록 tick마다 O(N) 페이로드.
3. **`match-info` 1회당 외부 호출 3회.** game server `/active` 1회 + account server `getUserDetail` 2회
   (`LegacyGameMatchService.java:65-95`). 매칭 직후 동시 폭주 구간.
4. **DB user status write가 아무도 안 읽는 쓰기.** `markMatching/markPlaying/markOnline`이
   매 전이마다 UPDATE 하지만 `getStatus()`는 이 컬럼을 읽지 않는다. (컬럼 자체는 admin이
   bot 편집용으로 사용 — `admin/BotAdminService.java:139`. 컬럼 유지, 핫패스 write만 제거.)

### 안정성

5. **다중 instance 미대응.** `@Scheduled(fixedRate=5000) tryMatching()`에 lock 없음
   (`GameMatchService.kt:96`). instance 2개면 둘 다 같은 Redis queue를 읽고 같은 쌍을 뽑을 수 있다.
   read(ZRANGE) → write(ZREM)가 원자적이지 않아 같은 user가 두 session에 배정될 수 있다.
6. **tick 겹침.** `scope.launch`로 즉시 반환하므로 `fixedRate`의 비겹침 보장이 무효.
   game server 응답이 느리면 (해당 WebClient에 timeout 없음) 코루틴이 누적된다.
7. **session 생성 실패 = 조용한 매칭 실패.** 실패 시 두 user 모두 queue에서 제거 + Online 처리
   (`GameMatchService.kt:119-126`). 재시도·requeue 없음. client는 "실패"만 본다.
8. **game server 선택이 항상 첫 번째 ACTIVE.** capacity·부하 무시, health check 60초 주기
   (`GameServerManagementService.java:31-50`). 죽은 서버로 최대 60초간 session 배정.
9. **`MMR_KEY` hash leak.** `removeExpired()`는 ZSET만 지우고 hash entry는 남긴다
   (`MatchingQueueRepository.kt:63-76`).
10. **recovery TTL 5분.** `SessionRecoveryStore` TTL / `SessionRecoveryInfo` TTL 모두 5분.
    5분 넘는 게임은 재접속 복구 불가.

### 매칭 품질

11. **대기 시간 무시.** `findClosestPair`는 MMR 최근접 쌍만 고른다
    (`MatchingQueueRepository.kt:81-92`). 오래 기다린 user가 계속 밀릴 수 있다.
12. **tick당 1쌍.** queue 100명 전원 매칭에 약 250초.
13. **재enqueue가 대기 시간을 리셋.** `enqueue()`가 score를 `now`로 덮어쓴다
    (`MatchingQueueRepository.kt:20-24`). 대기 기반 정책의 전제를 깬다.
14. **ZSET score가 timestamp인데 정렬은 MMR로 필요.** Redis가 이미 해줄 정렬을 앱에서 다시 한다.

## 설계

### Redis key schema

| key | type | score/value | 용도 |
|---|---|---|---|
| `mq:mmr` | ZSET | score = mmr, member = userId | pairing (Redis가 정렬 담당) |
| `mq:wait` | ZSET | score = enqueuedAt(ms), member = userId | 대기 시간, 만료 스캔 |
| `mq:state:{userId}` | STRING(JSON) | `{state, sessionId, serverUrl, opponentId, updatedAt}` | 단일 상태 진실 소스 + recovery |
| `mq:lock:tick` | STRING | instanceId, `SET NX PX 3000` | tick 단일 owner |
| `mq:retry:{userId}` | STRING | 재시도 횟수, TTL 60s | session 생성 실패 재시도 한도 |
| `mq:counter` | STRING | INCR | sessionId 채번 (기존 유지) |

`state` enum: `QUEUED` / `MATCHED` / `PLAYING`. 키 없음 = `Online`.
TTL: `QUEUED` = `MATCH_TIMEOUT + 60s`, `MATCHED`/`PLAYING` = `GAME_MAX_DURATION`(기본 60분).

`mq:state`가 `SessionRecoveryStore`의 `matching:result:{userId}`를 대체한다. 상태 조회와 복구가
같은 키를 읽으므로 "queue에 있는데 state는 Online" 같은 이중화 불일치가 구조적으로 사라진다.

### 상태 조회 (핫패스)

```
GET /api/users/mine/status  ->  GET mq:state:{userId}  ->  state 그대로 반환 (없으면 Online)
```

outbound 호출 0. 폴링 2초는 그대로 둬도 Redis GET 1회면 부담이 아니다. 폴링 자체가 문제가 아니라
폴링 1회의 fan-out이 문제였다.

### reconciler (fan-out을 요청 경로 밖으로)

`@Scheduled(fixedDelay = 10s)`, tick lock 소유 instance만 실행:

1. `getAllGameSessions()` 1회 → 활성 room의 userId set.
2. `PLAYING`/`MATCHED` state key 중 room에 없고 `updatedAt`이 grace(30s) 지난 것 → state 삭제(Online 복귀).

비용이 사용자 수와 무관해진다. Phase 3에서 game server가 session 종료를 callback 하면 이 주기를
늘리거나 제거한다.

### matching tick

`@Scheduled(fixedDelay = 1000)`. `fixedRate` + `scope.launch` 조합을 버리고 **suspend 함수 직접
실행**(`fixedDelay`)으로 겹침을 없앤다. `mq:lock:tick`을 `SET NX PX 3000`으로 잡은 instance만 진행.

```
1. 만료 처리: ZRANGEBYSCORE mq:wait -inf (now - MATCH_TIMEOUT)
   -> mq:mmr, mq:wait, mq:state 에서 함께 제거 (hash leak 원인 제거)
2. 후보 로드: ZRANGE mq:mmr 0 -1 WITHSCORES  (mmr 오름차순, Redis 정렬)
              ZRANGE mq:wait 0 -1 WITHSCORES (대기 시각)
3. 인접쌍 1-pass 스윕:
   for i in 0..n-2:
     if consumed[i] or consumed[i+1]: continue
     diff = mmr[i+1] - mmr[i]
     tol  = max(tolerance(wait[i]), tolerance(wait[i+1]))
     if diff <= tol: pair 확정, consumed 표시
   (batch cap MAX_PAIRS_PER_TICK = 200, 초과분은 다음 tick)
4. 확정 쌍마다:
   ZREM mq:mmr uid1 uid2  -> 반환값이 2가 아니면 이 쌍 폐기 (동시성 최종 방어선)
   ZREM mq:wait uid1 uid2
   SET mq:state:{uid} MATCHED
5. 쌍별 병렬 createSession (동시성 제한 있음)
   성공: state = PLAYING (sessionId, serverUrl)
   실패: mq:retry INCR, 3회 미만이면 원래 enqueuedAt 유지한 채 requeue
         3회 이상이면 state 삭제 + 로그 (client는 Online 관측 -> 매칭 실패 표시)
```

`tolerance(waitMs)`:

```
tol = BASE_MMR_DIFF + (waitMs / 1000) * WIDEN_PER_SEC     (기본 100, 20)
if waitMs >= MATCH_MAX_WAIT (기본 60s): tol = Long.MAX_VALUE
```

Lua script는 쓰지 않는다. tick lock으로 단일 owner를 보장하고 `ZREM` 반환값으로 검증하면
원자성이 충분하다. lock 만료·GC pause로 owner가 겹치는 순간에도 `ZREM != 2`면 쌍이 버려지므로
"같은 user 두 session" 시나리오가 성립하지 않는다.

### enqueue

```
enqueue(userId):
  deck 검증
  mmr 조회
  ZADD mq:mmr  score=mmr userId
  ZADD mq:wait GT score=now userId     # GT: 기존 값이 있으면 갱신하지 않음 -> 대기 시간 보존
  SET mq:state:{userId} QUEUED EX ...
```

`ZADD ... GT`는 "더 큰 score만 갱신"이므로 재enqueue가 대기 시각을 앞당기지 못한다.
(리셋 방지 목적상 `NX`가 더 명확하면 `NX` 사용 — 구현 시 Redis 버전 확인.)

DB `users.status` write는 핫패스에서 제거한다. 컬럼은 admin 편집용으로 남긴다.

### game server 선택

- health check 주기 60초 → 10초, WebClient timeout 2초 유지.
- 실패 1회 즉시 INACTIVE 표시(현재도 그럼), 성공 시 복귀.
- `getAvailableServer()`를 round-robin + `RoomListDto` 기반 현재 room 수 참고로 교체.
  capacity 상한(`MAX_ROOMS_PER_SERVER`, config)을 넘으면 다음 서버로.
- `LegacyGameMatchService`의 session 생성 WebClient에 timeout 3초 추가.

### recovery TTL

`GAME_MAX_DURATION`(기본 60분)으로 늘린다. 종료는 reconciler가 감지해 지운다.

## Affected Repositories and Contracts

- `lobby`: 전 범위. HTTP contract 변경 없음 (`/api/users/mine/status`, `/api/users/mine/match-info`,
  `/api/match/*` 응답 shape 유지).
- `client`: **변경 없음.** Phase 2(long-poll/SSE)에서만 변경.
- `game`: **변경 없음.** Phase 3(session 종료 callback)에서만 변경.
- `database`: **변경 없음.** `users.status` 컬럼 유지.

## Approach

- [ ] **Step 0: Recon** — 운영 lobby instance 수, Redis 버전(`ZADD GT` 지원), 평균/최대 게임 길이,
      동시 접속 규모 확인. 미확인 값은 config 기본값으로 두고 문서화.
- [ ] **Step 1: state store** — `MatchStateStore` (Redis `mq:state:{userId}`) 신설.
      `SessionRecoveryStore` 대체, `getStatus()`를 GET 1회로 교체. game server fan-out 제거.
- [ ] **Step 2: queue repository 재작성** — `mq:mmr`/`mq:wait` 2 ZSET, 만료 시 전 키 정리,
      `dequeueBestPair` 제거, 배치 스윕용 `loadCandidates()` + `claimPair()` 추가.
- [ ] **Step 3: tick 재작성** — `fixedDelay` + tick lock + 배치 스윕 + tolerance + batch cap.
      requeue/재시도 한도 포함.
- [ ] **Step 4: reconciler** — 10초 주기 room list 대조로 종료된 session state 정리.
- [ ] **Step 5: server 선택** — health check 10초, round-robin + capacity, session 생성 timeout.
- [ ] **Step 6: 정리** — 핫패스 DB status write 제거, `matching:result:*` 키 마이그레이션 경로 확인.
- [ ] **Step 7: 테스트** — 아래 Validation.
- [ ] **Step 8: 계측** — tick 소요, tick당 매칭 쌍 수, queue 길이, 매칭 대기 시간 로그.

## Validation

- Commands:
  - `./gradlew compileJava compileKotlin`
  - `./gradlew test`
- 신규 테스트:
  - tolerance 스윕: MMR 근접 쌍 / 오래 대기한 원거리 쌍 / `MATCH_MAX_WAIT` 초과 시 무조건 매칭.
  - `claimPair()`가 `ZREM != 2`일 때 쌍을 버리는지 (동시 소비 시뮬레이션).
  - 만료 처리 후 `mq:mmr`, `mq:wait`, `mq:state` 어디에도 잔여 키가 없는지.
  - session 생성 실패 시 requeue + `enqueuedAt` 보존, 3회 초과 시 state 제거.
  - `getStatus()`가 `GameSessionService`를 호출하지 않는지 (mock 상호작용 검증).
- Manual checks:
  - lobby 2 instance 로컬 기동, 봇/스크립트로 queue 100명 투입 → 중복 session 0, 전원 매칭까지 소요 시간 측정.
  - 로비 100 client 폴링 상태에서 game server access log에 room list 요청이 10초당 1회만 찍히는지.
  - 매칭 중 `DELETE /api/match/queue/me` 스팸 → 세션 배정된 user가 Online으로 남지 않는지.
- Expected results:
  - status 요청당 outbound 0.
  - queue 100명 전원 매칭 ≤ 5초 (기존 약 250초).

## Risks & Rollback

- **Redis key schema 변경**: 배포 순간 queue에 있던 user는 유실된다. 무중단이 필요하면
  배포 전 queue drain(짧은 매칭 중단 공지) 또는 신규 key 병행 후 구 key 만료 대기.
  `matching:result:*`는 TTL 5분이라 자연 소멸.
- **reconciler grace 오판**: room list가 일시적으로 비면 진행 중 게임을 Online으로 되돌릴 수 있다.
  → grace 30초 + room list 조회 실패 시 reconcile skip(현재 `GameServerClient`는 실패 시 빈 리스트를
  반환하므로 **실패와 빈 결과를 구분하도록 수정 필요**).
- **tick lock 소유 instance 다운**: lock TTL 3초 후 다른 instance가 인수. 최대 3초 매칭 정지.
- **Rollback**: lobby app 이전 버전 재배포 + `mq:*` 키 삭제. client/game/database 변경이 없어
  단일 모듈 롤백으로 끝난다.

## Release Order

1. lobby 단독 배포 (Phase 1 전체). 선행 모듈 없음.
2. Phase 2 (long-poll/SSE): lobby 배포 → client 배포 (server 우선, 기존 endpoint 유지).
3. Phase 3 (game → lobby 종료 callback): lobby 수신 endpoint 배포 → game 발신 배포 → reconciler 주기 완화.

## Open Questions

1. 운영 lobby instance는 현재 몇 개인가? 1개면 tick lock은 안전망, 2개 이상이면 필수.
2. 게임 1판 최대 길이는? `GAME_MAX_DURATION` 기본값(60분) 확정 필요.
3. game server 1대당 동시 room 상한은? capacity 기반 선택의 임계값.
4. `MATCH_MAX_WAIT` 60초 / `BASE_MMR_DIFF` 100 / `WIDEN_PER_SEC` 20 — 실제 MMR 분포 기준으로 재조정 필요.
5. 배포 시 매칭 대기열 drain을 허용할 수 있는가 (짧은 매칭 중단)?
