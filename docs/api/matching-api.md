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
