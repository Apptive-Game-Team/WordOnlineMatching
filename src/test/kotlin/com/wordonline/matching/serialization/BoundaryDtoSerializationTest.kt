package com.wordonline.matching.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.wordonline.matching.auth.dto.UserDetailResponseDto
import com.wordonline.matching.matching.domain.CancelMatchResponse
import com.wordonline.matching.matching.domain.CancelMatchResult
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.MatchTicketState
import com.wordonline.matching.matching.dto.AccountMemberResponseDto
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.session.domain.SessionRecoveryInfo
import com.wordonline.matching.session.dto.CreateSessionRequest
import com.wordonline.matching.session.dto.SessionReadyResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.Instant

/**
 * Every DTO here crosses a process boundary - Redis, the game server, or the account
 * server - so it is only ever as correct as Jackson's ability to rebuild it.
 *
 * The rest of the suite mocks those boundaries away, which is why a `MatchedInfoDto` that
 * no `ObjectMapper` could construct shipped with 51 green tests. These round trips use the
 * mapper Spring Boot actually builds, so a class that loses its creator - by gaining a
 * second constructor, a default argument, or `@JvmOverloads` - fails here first.
 */
class BoundaryDtoSerializationTest {

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    private inline fun <reified T : Any> roundTrip(value: T): T =
        objectMapper.readValue(objectMapper.writeValueAsString(value), T::class.java)

    // ---------------------------------------------------------------- Redis

    @Test
    fun `matched ticket survives a redis round trip`() {
        val ticket = matchedTicket()

        assertThat(roundTrip(ticket)).isEqualTo(ticket)
    }

    @Test
    fun `queued ticket keeps its null fields through a redis round trip`() {
        val ticket = MatchTicket(
            ticketId = "ticket-2",
            userId = 2L,
            mmr = 1100L,
            state = MatchTicketState.QUEUED,
            version = 1L,
            createdAt = Instant.parse("2026-08-10T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )

        val decoded = roundTrip(ticket)

        assertThat(decoded).isEqualTo(ticket)
        assertThat(decoded.matchInfo).isNull()
        assertThat(decoded.attemptId).isNull()
        assertThat(decoded.allocationLeaseUntil).isNull()
    }

    @Test
    fun `allocation lease instant keeps millisecond precision through a redis round trip`() {
        val leaseUntil = Instant.parse("2026-08-10T00:00:03.250Z")
        val ticket = matchedTicket().copy(
            state = MatchTicketState.ALLOCATING,
            allocationLeaseUntil = leaseUntil,
        )

        // Lease expiry is compared against `now`, so a lost fraction silently shifts the deadline.
        assertThat(roundTrip(ticket).allocationLeaseUntil).isEqualTo(leaseUntil)
    }

    @Test
    fun `session recovery info survives a redis round trip`() {
        // SessionRecoveryStore writes this record to Redis and reads it back on reconnect.
        val info = SessionRecoveryInfo(1L, 2L, "session-1", "http://localhost:7777", 1_786_000_000_000L)

        val decoded = roundTrip(info)

        assertThat(decoded).isEqualTo(info)
        assertThat(decoded.serverUrl()).isEqualTo("http://localhost:7777")
    }

    // ----------------------------------------------------- Game server HTTP

    @Test
    fun `session ready response from the game server is readable`() {
        val response = SessionReadyResponse(
            attemptId = "attempt-1",
            sessionId = "session-1",
            ready = true,
            serverUrl = "http://localhost:7777",
            webSocketUrl = "http://localhost:7777/ws",
        )

        assertThat(roundTrip(response)).isEqualTo(response)
    }

    @Test
    fun `create session request keeps a null uid2 for solo sessions`() {
        val request = CreateSessionRequest("attempt-1", SessionDto.PVE("session-1", 1L, 7L))

        val decoded = roundTrip(request)

        assertThat(decoded).isEqualTo(request)
        assertThat(decoded.uid2).isNull()
        assertThat(decoded.scenarioId).isEqualTo(7L)
    }

    // -------------------------------------------------- Account server HTTP

    @Test
    fun `account member response is readable`() {
        val member = AccountMemberResponseDto(email = "player@example.com", name = "player")

        assertThat(roundTrip(member)).isEqualTo(member)
    }

    @Test
    fun `user detail response is readable`() {
        val user = UserDetailResponseDto(1L, "player", "player@example.com")

        assertThat(roundTrip(user)).isEqualTo(user)
    }

    // ------------------------------------------------------ Client responses

    @Test
    fun `matched info sent to the client keeps its websocket url`() {
        val matchedInfo = matchedInfo()

        val decoded = roundTrip(matchedInfo)

        assertThat(decoded).isEqualTo(matchedInfo)
        assertThat(decoded.webSocketUrl).isEqualTo("http://localhost:7777/ws")
    }

    @Test
    fun `cancel response carries its result and ticket`() {
        val response = CancelMatchResponse(CancelMatchResult.TOO_LATE, matchedTicket())

        assertThat(roundTrip(response)).isEqualTo(response)
    }

    @Test
    fun `cancel response for an unknown ticket keeps a null ticket`() {
        val response = CancelMatchResponse(CancelMatchResult.NOT_FOUND, null)

        assertThat(roundTrip(response)).isEqualTo(response)
    }

    private fun matchedInfo() = MatchedInfoDto(
        message = "Successfully Matched",
        server = "http://localhost:7777",
        leftUser = UserDetailResponseDto(1L, "left", "left@example.com"),
        rightUser = UserDetailResponseDto(2L, "right", "right@example.com"),
        sessionId = "session-1",
        webSocketUrl = "http://localhost:7777/ws",
    )

    private fun matchedTicket() = MatchTicket(
        ticketId = "ticket-1",
        userId = 1L,
        mmr = 1200L,
        state = MatchTicketState.MATCHED,
        version = 3L,
        matchInfo = matchedInfo(),
        attemptId = "attempt-1",
        createdAt = Instant.parse("2026-08-10T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-10T00:00:05Z"),
    )
}
