# Matching API

매칭 관련 API입니다. `/api/match/length`를 제외한 모든 엔드포인트는 JWT Bearer 토큰 인증이 필요합니다.

---

## Practice Match

### `GET /api/match/practice/me`

봇과의 연습 매칭을 즉시 시작합니다.

**인증:** 필요

### Response Body

```json
{
  "type": "matchedInfoDto",
  "message": "Successfully Matched",
  "server": "https://game-server.example.com",
  "sessionId": "bot-42",
  "leftUser": { "id": 1, "name": "Alice", "email": "alice@example.com" },
  "rightUser": { "id": -1, "name": "master of everything", "email": "bot@team6515.com" }
}
```

| Field | Type | Description |
|---|---|---|
| `type` | String | 항상 `"matchedInfoDto"` |
| `message` | String | 결과 메시지 |
| `server` | String | 접속해야 할 게임 서버 URL |
| `sessionId` | String | 생성된 세션 ID |
| `leftUser` | UserDetail | 요청한 유저 정보 |
| `rightUser` | UserDetail | 상대방(봇) 유저 정보 |

**UserDetail**

| Field | Type | Description |
|---|---|---|
| `id` | Long | 유저 ID (봇은 음수) |
| `name` | String | 유저 이름 |
| `email` | String | 유저 이메일 |

---

## PVP Matchmaking Queue

### `GET /api/match/queue/me`

현재 유저를 PVP 매칭 큐에 등록합니다. 매칭은 서버에서 **5초마다** 자동으로 시도됩니다.

> **주의:** 이 엔드포인트는 즉시 응답을 반환합니다. 매칭 결과는 별도로 확인해야 합니다.

**인증:** 필요

**사전 조건:** 덱이 선택되어 있어야 합니다.

### Response Body

성공 시:

```json
{ "type": "message", "message": "Successfully Enqueued" }
```

실패 시 (덱 미선택 등):

```json
{ "type": "message", "message": "Failed to enqueue user" }
```

---

### `GET /api/match/queue/me/exist`

현재 유저가 매칭 큐에 있는지 확인합니다.

**인증:** 필요

### Response

| Status | 설명 |
|---|---|
| `200 OK` | 큐에 있음 |
| `404 Not Found` | 큐에 없음 |

Response body는 비어 있습니다.

---

### `DELETE /api/match/queue/me`

현재 유저를 매칭 큐에서 제거합니다.

**인증:** 필요

**Response:** `200 OK`, body 없음

---

## Queue Status

### `GET /api/match/length`

현재 매칭 큐에 대기 중인 유저 수를 반환합니다.

**인증:** 불필요

### Response Body

```json
{ "length": 4 }
```

| Field | Type | Description |
|---|---|---|
| `length` | Int | 현재 큐 대기 인원 수 |

---

## 변경 사항

> feature/33 기준 변경 내용입니다.

| 항목 | 이전 | 이후 |
|---|---|---|
| `GET /api/match/queue/me` 응답 방식 | SSE 스트림 (`text/event-stream`) — 매칭 완료 시 `MatchedInfoDto` 푸시 | JSON 즉시 응답 — 큐 등록 결과만 반환 |
| 매칭 시도 주기 | 1초 | **5초** |
| 큐 저장소 | 인메모리 (`ConcurrentLinkedQueue`) | **Redis** (`matching:queue` Set) |
| 세션 정보 저장소 | 인메모리 (`ConcurrentHashMap`) | **Redis** (`matching:result:{userId}`, TTL 10분) |
| 세션 ID 생성 | 인메모리 `AtomicInteger` | **Redis** `INCR matching:session-counter` |

## MatchTicket 상태 및 복구

Redis `MatchTicket`이 매칭 상태의 source of truth다. 상태는
`QUEUED`, `ALLOCATING`, `MATCHED`, `CANCELED`, `EXPIRED`, `FAILED` 중 하나이며,
모든 변경마다 `version`이 증가한다. 클라이언트는 더 큰 version만 적용한다.

- `GET /api/match/tickets/active`: 현재 사용자의 최신 ticket snapshot 조회
- `POST /api/match/tickets`: ticket 생성 후 전체 snapshot 반환
- `DELETE /api/match/tickets/{ticketId}`: 사용자 활성 ticket과 ID가 일치할 때만 취소
- `GET /api/match/events`: best-effort SSE 상태 변경 알림
- `DELETE /api/match/queue/me`: `CANCELED`, `TOO_LATE`, `ALREADY_FINISHED`, `NOT_FOUND` 결과 반환

SSE 이벤트는 보관하거나 replay하지 않는다. 연결이 끊겼다가 복구되면 클라이언트는
`GET /api/match/tickets/active`를 호출해 누락된 변경의 최종 snapshot을 동기화해야 한다.
`ALLOCATING` lease가 만료되면 ticket은 원자적으로 `QUEUED`로 복구된다.

`MATCHED`는 terminal 상태가 아니므로 TTL이 붙지 않는다. `terminal-ttl`은
`CANCELED`, `EXPIRED`, `FAILED`에만 적용되며, terminal 전이 시
`matching:active:<userId>`가 그 ticket을 가리키고 있을 때만 함께 해제된다.

---

## Session Lost Report

### `POST /api/match/sessions/{sessionId}/report-lost`

게임 서버 세션에 더 이상 접속할 수 없다고 신고한다. 게임 서버는 세션을 인메모리로만
들고 있어서 프로세스가 재시작하면 세션이 로비에 알림 없이 사라지고, ticket이
`MATCHED`에 고착돼 재큐가 막힌다. 이 엔드포인트가 그 고착을 푼다.

경로가 ticket이 아니라 session 기준인 이유는 클라이언트가 legacy 매칭 흐름을 쓰기 때문에
`sessionId`만 알고 `ticketId`는 모르기 때문이다. ticket은 서버가 토큰의 사용자로 찾는다.

**인증:** 필요

> **신고는 근거일 뿐 판정이 아니다.** 종료 여부는 로비가 직접 확인한다. 클라이언트가
> 신고했다는 사실만으로 살아있는 세션이 끝나는 일은 없다.

로비의 판정 순서:

1. 활성 ticket의 `matchInfo.sessionId`가 경로의 `sessionId`와 다르면 `404`.
2. ticket state가 `MATCHED`가 아니면 이미 자유로운 상태이므로 현재 snapshot을 그대로 `200`.
3. ticket에 기록된 게임 서버 부팅 세대값(`instance_id`)이 `servers` 행의 현재 값과 다르면
   호스트 프로세스가 재시작한 것이므로 세션 소실로 확정한다. 어느 한쪽이라도 값이 없으면
   (구버전 게임 서버) 판단 근거로 쓰지 않고 다음 단계로 넘어간다.
4. 호스트에 `GET /api/server/game-sessions/{sessionId}/active`를 조회한다.

### Response

| Status | 의미 | Body |
|---|---|---|
| `200 OK` | ticket이 `FAILED(SESSION_LOST)`로 정리됐거나, 정리할 것이 없었다 | MatchTicket |
| `409 Conflict` | 세션이 살아있다. ticket은 그대로 둔다 | MatchTicket |
| `404 Not Found` | 활성 ticket이 없거나 다른 세션이다 | 없음 |
| `503 Service Unavailable` | 호스트 무응답. 종료 확정이 아니므로 ticket을 유지한다 | 메시지 |

세션 소실이 확정되면 **상대방 ticket도 함께 정리한다.** 단, `matching:active:<상대userId>`가
가리키는 ticket의 `matchInfo.sessionId`가 같은 세션일 때만이다. 상대가 이미 재큐해 새 ticket을
들고 있으면 그 ticket은 건드리지 않는다. 정리된 사용자는 모두 `user_status`가 `Online`으로 돌아간다.

`503`을 받은 클라이언트는 재시도해도 되지만, 로비가 해당 ticket을 재확인 대기열에 넣어
`matching.ticket.pending-scan-interval` 주기로 다시 판정하므로 별도 폴링 없이도 곧 결론이 난다.

## MATCHED reconciler

양쪽 클라이언트가 모두 이탈해 아무도 신고하지 않는 경우의 안전망이다. `matching:matched`
ZSET 인덱스를 주기적으로 훑어 위와 같은 기준으로 `FAILED(SESSION_LOST)` 전환과 `markOnline`을
수행한다. 호스트가 한 번 응답하지 않았다는 이유로 세션을 끝내지 않고, `ServerHealthRegistry`가
`gameserver.failure-threshold`회 연속 실패로 서버를 로테이션에서 뺀 뒤에야 확정한다.

| 설정 | 기본값 | 설명 |
|---|---|---|
| `matching.ticket.matched-scan-interval` | `30s` | 전체 스캔 주기. 세션 수명보다 훨씬 크게 잡으면 사실상 비활성화된다 |
| `matching.ticket.pending-scan-interval` | `3s` | 신고됐지만 판정하지 못한 ticket의 재확인 주기 |
| `matching.ticket.matched-scan-grace` | `30s` | `MATCHED`가 된 뒤 이 시간이 지나야 감사 대상이 된다 |
| `matching.ticket.matched-scan-batch-size` | `100` | 한 번의 스캔에서 감사할 ticket 수 |

## 게임 서버 연동

게임 서버의 `POST /api/server/game-sessions` 응답(`SessionReadyResponse`)에 `instanceId`가
추가됐다. 프로세스 부팅마다 새로 생성되는 UUID이며, 게임 서버가 `public.servers.instance_id`
(`VARCHAR(64)`, nullable, migration `V041_20260810__add_server_instance_id.sql`)에도 같은 값을 쓴다.
로비는 매칭 시점의 값을 ticket에 보관했다가 현재 값과 비교해 호스트 재시작을 판별한다.
`NULL`은 "아직 보고되지 않음"이며 재시작 증거가 아니다.
