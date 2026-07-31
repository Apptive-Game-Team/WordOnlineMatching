# 2026-07-31 — [대안] match_tickets 단일 테이블 + SKIP LOCKED + long-poll 매칭

- Date: 2026-07-31
- GitHub Issue: WordOnlineMatching #78 (선행: WordOnlineDatabase #29, 후속: WordOnlineClient #430)
- Owning repository: `lobby` (WordOnlineMatching) — 단, 스키마는 `database`, 폴링 변경은 `client`
- Status: In progress (채택안. 기존안: `.plan/general/2026-07-31-stable-low-load-matchmaking.md`)

## Goal

- 매칭 상태를 **한 곳(Postgres `match_tickets` 행)** 에만 둬서 상태 이중화를 구조적으로 없앤다.
- 동시성을 애플리케이션 lock이 아니라 **`FOR UPDATE SKIP LOCKED`** 로 DB에 위임한다. lobby instance가
  늘어나면 경합이 아니라 처리량 증가가 된다.
- 상태 조회를 2초 폴링에서 **long-poll(최대 25초 보류)** 로 바꿔 요청 수를 한 자릿수 분율로 줄인다.
- 매칭 확정을 트랜잭션 하나로 커밋해 "session은 만들어졌는데 상태 저장 실패" 같은 부분 실패 구간을 없앤다.

## 기존안과의 차이 (왜 다른 계획인가)

| 축 | 기존안 (Redis 중심) | 이 대안 (DB 중심) |
|---|---|---|
| 상태 저장소 | Redis ZSET 2개 + state 키 | `match_tickets` 테이블 1개 |
| 동시성 제어 | tick lock(`SET NX PX`) + `ZREM` 반환값 검증 | `FOR UPDATE SKIP LOCKED` (DB가 보장) |
| 다중 instance | 한 instance만 tick 실행(나머지 유휴) | 모든 instance가 서로 다른 구간을 동시 매칭 |
| 부분 실패 | Redis 명령 여러 개, 중간 실패 구간 존재 | 단일 트랜잭션 커밋 |
| 조회 부하 | 폴링 유지, 1회 비용을 O(1)로 | 폴링 제거(long-poll) |
| 변경 범위 | lobby 단독 | lobby + database migration + client |
| 인프라 | Redis 필수 | 매칭 경로에서 Redis 제거 가능 |
| 적합 규모 | 동시 대기열 수천 이상 | 동시 대기열 수백~수천 |

기존안은 "지금 구조를 더 빠르고 안전하게" 고치는 안이고, 이 대안은 "상태가 4곳(Redis queue /
Redis recovery / DB `users.status` / game server room list)에 흩어진 것 자체"를 문제로 보고
1곳으로 합치는 안이다. 진단(문제 목록)은 기존안 문서의 *Context / Constraints* 절을 그대로 따른다.

## Acceptance Criteria

1. 매칭 대기·확정·플레이 상태가 `match_tickets` 한 행에만 존재한다. Redis에는 매칭 상태가 없다.
2. `GET /api/users/mine/status` 처리 중 outbound HTTP 0회, DB query 1회.
3. lobby instance 2개 동시 기동 + 대기열 200명에서 동일 user 중복 session 0건, 두 instance 모두 매칭에 기여.
4. long-poll 적용 후 lobby 기준 status 요청률이 기존 대비 1/10 이하.
5. session 생성 실패 시 ticket이 `QUEUED`로 복귀하고 `enqueued_at`이 보존된다.
6. `wait` 파라미터 없는 구버전 client도 그대로 동작한다(즉시 응답).

## Non-goals

- MMR 계산/랭크 개편.
- game server → lobby session 종료 callback (여기서도 reconciler로 대체, 후속 과제).
- party/친선전, region 분리.
- Redis 전면 제거 (deck·quest 등 다른 용도는 그대로 둔다).

## Context / Constraints

- DB는 Postgres (`UserRepository.java:30`의 `CAST(:status AS user_status)`), 접근은 R2DBC.
  `FOR UPDATE SKIP LOCKED` 사용 가능. `UserService`가 이미 `@Transactional`이라 트랜잭션 인프라 존재.
- 스키마는 `database` repo 소유. lobby 런타임 리소스에 production SQL을 넣지 않는다 (lobby `AGENTS.md`).
- `users.status` 컬럼은 admin bot 편집에서 사용 중(`admin/BotAdminService.java:139`) → 컬럼 유지,
  매칭 핫패스 write만 제거.
- game server는 lobby로 아무것도 호출하지 않는다 (game 모듈에 outbound WebClient 없음).
  세션 종료 감지는 여전히 lobby가 room list를 대조해야 한다.
- client는 Unity(WebGL 포함). SSE는 WebGL `UnityWebRequest` 스트리밍 제약이 있어 **long-poll을 택한다**.
  일반 GET에 timeout만 늘리면 되므로 client 변경이 작다.

## 설계

### 스키마 (database repo)

```sql
CREATE TYPE match_ticket_state AS ENUM ('QUEUED', 'MATCHED', 'PLAYING');

CREATE TABLE match_tickets (
    user_id      BIGINT PRIMARY KEY,
    mmr          BIGINT      NOT NULL,
    state        match_ticket_state NOT NULL,
    enqueued_at  TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    session_id   TEXT,
    server_url   TEXT,
    opponent_id  BIGINT,
    retry_count  INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_match_tickets_queued ON match_tickets (mmr) WHERE state = 'QUEUED';
CREATE INDEX idx_match_tickets_updated ON match_tickets (state, updated_at);

CREATE SEQUENCE match_session_seq;   -- Redis nextSessionId 대체
```

부분 인덱스(`WHERE state='QUEUED'`)라 대기열 크기만큼만 스캔한다.

### enqueue

```sql
INSERT INTO match_tickets (user_id, mmr, state, enqueued_at, updated_at)
VALUES (:userId, :mmr, 'QUEUED', now(), now())
ON CONFLICT (user_id) DO UPDATE
SET mmr = EXCLUDED.mmr,
    state = 'QUEUED',
    updated_at = now()
    -- enqueued_at 은 갱신하지 않음: 재요청이 대기 시간을 리셋하지 못하게
WHERE match_tickets.state = 'QUEUED';
```

`WHERE match_tickets.state = 'QUEUED'` 조건 덕에 이미 `PLAYING`인 user가 다시 큐에 들어가지 못한다.
현재 코드에는 이 방어가 없다.

### 매칭 tick (`@Scheduled(fixedDelay = 1000)`, 모든 instance가 실행)

트랜잭션 1:

```sql
SELECT user_id, mmr, enqueued_at
FROM match_tickets
WHERE state = 'QUEUED'
ORDER BY mmr
LIMIT 400
FOR UPDATE SKIP LOCKED;
```

- 앱에서 인접쌍 1-pass 스윕. tolerance는 기존안과 동일:
  `tol = BASE_MMR_DIFF + 대기초 * WIDEN_PER_SEC`, `MATCH_MAX_WAIT` 초과 시 무제한.
- 확정 쌍은 같은 트랜잭션에서
  `UPDATE match_tickets SET state='MATCHED', opponent_id=?, session_id=?, updated_at=now() WHERE user_id IN (?,?)`
- COMMIT.

`SKIP LOCKED`가 핵심이다. instance A가 잡은 400행은 instance B가 **건너뛰고** 다음 400행을 잡는다.
lock도, Lua도, `ZREM` 반환값 검증도 필요 없고, instance를 늘리면 매칭 처리량이 늘어난다.
같은 user가 두 트랜잭션에 동시에 잡히는 경우가 원천적으로 없다.

트랜잭션 밖 (외부 HTTP를 트랜잭션 안에 넣지 않는다):

```
쌍마다 병렬 createSession(timeout 3s)
  성공 -> UPDATE state='PLAYING', server_url=?, updated_at=now()
  실패 -> UPDATE state='QUEUED', retry_count=retry_count+1, session_id=NULL
          (enqueued_at 보존 -> 오래 기다린 만큼 tolerance 유지)
          retry_count >= 3 -> DELETE (client는 Online 관측 -> 매칭 실패 표시)
```

`MATCHED` 상태로 멈춰 있는 행(프로세스가 이 구간에서 죽은 경우)은 청소 tick이
`state='MATCHED' AND updated_at < now() - 30s` 조건으로 `QUEUED` 복귀시킨다. 이 복구가 공짜로
생기는 것이 Redis 다단 명령 대비 이 설계의 이점이다.

### 상태 조회 = long-poll

```
GET /api/users/mine/status?wait=25
```

- `wait` 없음 → 현재 동작 그대로 즉시 응답 (구버전 client 호환).
- `wait=N` → 상태가 바뀔 때까지 최대 N초 보류 후 응답. 변화 없으면 마지막 상태로 응답.
- 구현: WebFlux `Mono` + `Sinks.Many<Long>`(상태가 바뀐 userId 발행) + `timeout(N)` fallback.
  매칭 tick이 상태를 바꾼 userId를 sink에 발행한다.
- 다중 instance: 대기 중인 client가 붙은 instance와 매칭한 instance가 다를 수 있으므로
  Postgres `LISTEN/NOTIFY`로 브로드캐스트 (R2DBC Postgres 지원). NOTIFY 페이로드 = userId.
  **instance가 1개면 sink만으로 충분하고 NOTIFY는 생략 가능** — 운영 instance 수 확인 후 결정.
- 최악의 fallback: NOTIFY를 쓰지 않고 long-poll 내부에서 1초 간격으로 자기 행만 재조회.
  요청 수는 그대로 1/12로 줄고 DB 쿼리는 기존 폴링과 동일하다. 이 경우에도 game server fan-out은 사라진다.

WebFlux는 non-blocking이라 25초 보류가 스레드를 잡지 않는다. 이 설계가 성립하는 전제.

### 세션 종료 감지 (reconciler)

game 모듈 변경 없이 유지:

- `@Scheduled(fixedDelay = 10s)`, advisory lock(`pg_try_advisory_lock`)으로 instance 1개만 실행.
- `getAllGameSessions()` 1회 → 활성 room userId set.
- `state='PLAYING' AND updated_at < now() - 30s AND user_id NOT IN (활성 room)` → DELETE.
- **`GameServerClient.getGameSessions()`가 실패 시 빈 리스트를 반환하는 현재 동작은 반드시 수정**
  (`GameServerClient.java:30-33`). 실패와 "방 없음"을 구분하지 못하면 진행 중 게임을 종료로 오판한다.
  조회 실패한 서버가 하나라도 있으면 이번 reconcile은 skip.

### 정리되는 것들

- `MatchingQueueRepository` 삭제 (ZSET/hash leak 문제 동반 소멸).
- `SessionRecoveryStore` 삭제 → `match_tickets` 행이 recovery 정보를 겸함. 5분 TTL 제약 소멸.
- `UserService.getStatus()`의 game server fan-out 삭제.
- 핫패스 `markMatching/markPlaying/markOnline` DB write 삭제 (컬럼은 admin용으로 존치).
- `nextSessionId()` Redis INCR → `match_session_seq`.

## Affected Repositories and Contracts

- `database`: `match_tickets` 테이블 + enum + 인덱스 + sequence migration. **선행 배포 필수.**
- `lobby`: 매칭/상태 로직 전면 교체. HTTP 응답 shape 유지, `status`에 선택적 `wait` 쿼리 파라미터 추가(하위 호환).
- `client`: `MatchStatusPoller`가 `?wait=25` + `UnityWebRequest.timeout=30` 사용하도록 변경.
  **서버 배포 후에 해도 되는 선택적 변경** (안 해도 동작).
- `game`: 변경 없음.

## Approach

- [ ] **Step 0: Recon** — 운영 lobby instance 수, Postgres 커넥션 풀 여유, 동시 접속 규모,
      게임 1판 최대 길이 확인. instance 1개면 `LISTEN/NOTIFY` 생략.
- [ ] **Step 1: database migration** — `match_tickets` 스키마 (database repo, 단독 PR·선배포).
- [ ] **Step 2: repository** — `MatchTicketRepository` (enqueue upsert, `SKIP LOCKED` claim,
      상태 전이, 만료/스턱 청소). R2DBC 커스텀 쿼리.
- [ ] **Step 3: tick** — `fixedDelay` 배치 스윕 + tolerance + 트랜잭션 경계(외부 HTTP는 트랜잭션 밖).
- [ ] **Step 4: 상태 조회** — `mq:state` 대신 ticket 조회. `wait` 파라미터 + sink 기반 long-poll.
- [ ] **Step 5: reconciler** — advisory lock + room list 대조. `GameServerClient` 실패/빈 결과 구분.
- [ ] **Step 6: 제거** — `MatchingQueueRepository`, `SessionRecoveryStore`, fan-out, 핫패스 status write.
- [ ] **Step 7: 테스트** — 아래 Validation.
- [ ] **Step 8: client** — `?wait=25` + timeout 30초 (별도 PR).

## Validation

- Commands:
  - `./gradlew compileJava compileKotlin`
  - `./gradlew test`
- 신규 테스트:
  - tolerance 스윕: 근접 쌍 / 오래 대기한 원거리 쌍 / `MATCH_MAX_WAIT` 초과 시 무조건 매칭.
  - `SKIP LOCKED` claim: 두 트랜잭션 동시 실행 시 겹치는 user 0 (Testcontainers Postgres 권장,
    미도입이면 통합 테스트 범위 명시 후 수동 검증).
  - enqueue upsert가 `enqueued_at`을 보존하는지, `PLAYING` 상태에서 재enqueue가 거부되는지.
  - session 생성 실패 → `QUEUED` 복귀 + `retry_count` 증가, 3회 초과 시 DELETE.
  - `MATCHED` 30초 초과 스턱 행이 `QUEUED`로 복구되는지.
  - long-poll: 상태 변경 시 즉시 응답, 무변경 시 `wait` 후 응답, `wait` 없으면 즉시 응답.
  - `getStatus()`가 `GameSessionService`를 호출하지 않는지.
- Manual checks:
  - lobby 2 instance + 대기열 200명 스크립트 투입 → 중복 session 0, 두 instance 로그 모두 매칭 기록.
  - room list 조회를 강제 실패시켰을 때 진행 중 세션이 종료 처리되지 않는지.
  - 구버전 client(`wait` 미전송)로 매칭 전 과정 정상 동작.
- Expected results:
  - 대기열 200명 전원 매칭 ≤ 5초.
  - status 요청률 기존 대비 1/10 이하.

## Risks & Rollback

- **Postgres 부하·SPOF**: 매칭이 DB에 의존하게 된다. 동시 대기열이 수천을 넘으면 tick의
  `SKIP LOCKED` 스캔과 커넥션 풀이 병목이 될 수 있다. → 부분 인덱스 + `LIMIT 400` batch cap +
  커넥션 풀 여유 확인. 이 규모를 넘으면 기존 Redis안으로 전환.
- **long-poll 커넥션 점유**: WebFlux라 스레드는 안 잡지만 소켓·프록시 타임아웃 설정이 필요.
  reverse proxy idle timeout > 30초 확인.
- **`LISTEN/NOTIFY` 미지원/불안정**: fallback으로 long-poll 내부 1초 재조회. 부하 이득은 유지된다.
- **migration 선후 관계**: `database` 배포 전에 lobby를 올리면 전량 실패. 릴리스 순서 엄수.
- **Rollback**: lobby 이전 버전 재배포. `match_tickets`는 남겨둬도 무해(기존 코드가 참조하지 않음).
  롤백 순간 대기열은 유실(짧은 매칭 중단). client는 변경이 선택적이라 롤백 불필요.

## Release Order

1. `database`: `match_tickets` migration 배포.
2. `lobby`: 신규 매칭 배포 (`wait` 미사용 client와 호환).
3. `client`: `?wait=25` 적용 배포 (선택, 요청량 감소 목적).
4. 후속: game → lobby 세션 종료 callback 도입 후 reconciler 주기 완화.

## Open Questions

1. 운영 lobby instance 수 — 1개면 `LISTEN/NOTIFY` 생략하고 in-process sink만 쓴다.
2. Postgres 커넥션 풀 크기와 현재 사용률. tick 트랜잭션 + long-poll이 추가로 얼마를 쓸 수 있는가?
3. Testcontainers 도입 가능 여부 (`SKIP LOCKED` 동시성 검증에 사실상 필요).
4. reverse proxy(있다면) idle timeout이 30초 이상인가?
5. 목표 동시 대기열 규모 — 수백이면 이 안, 수천 이상 지속이면 기존 Redis안.
6. `MATCH_MAX_WAIT` 60초 / `BASE_MMR_DIFF` 100 / `WIDEN_PER_SEC` 20 — 실제 MMR 분포로 재조정 필요.
