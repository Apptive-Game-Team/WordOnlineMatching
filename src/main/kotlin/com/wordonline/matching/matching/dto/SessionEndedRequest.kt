package com.wordonline.matching.matching.dto

/**
 * Body of the game server's end-of-session notification.
 *
 * [instanceId] is the sender's boot generation - the same value it publishes on
 * `SessionReadyResponse` and writes to `servers.instance_id` - and lets the lobby drop a
 * notification that a since-replaced process sent about a session it no longer owns.
 *
 * Nullable so a body missing the field fails as a `400` from the handler rather than a
 * Jackson deserialization error the global handler would turn into a `500`.
 */
data class SessionEndedRequest(
    val instanceId: String? = null,
)
