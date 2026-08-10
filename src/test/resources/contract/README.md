# Game server contract fixtures

These files are the wire format of the calls this server and the game server make to each
other, shared with `Apptive-Game-Team/WordOnlineServer`. The same files exist there under
`src/test/resources/contract/` with identical content.

Session creation, `POST /api/server/game-sessions` (this server calls the game server):

- `create-session-request.json` - what this server sends. Verified here by serializing
  `CreateSessionRequest`; verified there by deserializing into the game server's own
  `CreateSessionRequest`.
- `session-ready-response.json` - what the game server answers. Verified here by
  deserializing into `SessionReadyResponse`; verified there by serializing the game
  server's own `SessionReadyResponse`.

Session end, `POST /api/internal/game-sessions/{sessionId}/ended` (the game server calls
this server, authenticated with a pre-issued service token):

- `session-ended-notification.json` - what the game server sends when a session finishes.
  Verified here by deserializing into `SessionEndedRequest`; verified there by serializing
  the game server's own notification DTO. The session id travels in the path, so the body
  carries only the `instanceId` of the process that ran the session: this server closes the
  ticket only while that id still matches what it recorded, which stops a restarted process
  from closing a ticket it never owned.

Changing a field name or the nesting on one side alone makes the other side's test fail,
which is the point: without these, both repositories' suites stay green through a broken
contract. A mismatch does not surface as an exception at runtime either - the lobby reads
it as a refusal and fails over, so every candidate is rejected and matching returns 503
with nothing in the logs pointing at the cause.

When the contract changes, update both copies in the same pull request pair and say so in
each body.
