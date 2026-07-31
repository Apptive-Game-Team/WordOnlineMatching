package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.config.MatchingProperties
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.MatchTicketState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MatchSweepTest {

    private val now: Instant = Instant.parse("2026-07-31T00:00:00Z")
    private val properties = MatchingProperties().apply {
        baseMmrDiff = 100
        widenPerSec = 20
        maxWaitSeconds = 60
    }

    private fun ticket(userId: Long, mmr: Long, waitedSeconds: Long) = MatchTicket(
        userId = userId,
        mmr = mmr,
        state = MatchTicketState.QUEUED,
        enqueuedAt = now.minusSeconds(waitedSeconds),
        updatedAt = now.minusSeconds(waitedSeconds),
        sessionId = null,
        serverUrl = null,
        leftUserId = null,
        rightUserId = null,
        retryCount = 0,
    )

    @Test
    fun `MMR이 가까우면 바로 매칭된다`() {
        val tickets = listOf(ticket(1, 1000, 0), ticket(2, 1050, 0))

        val pairs = MatchSweep.pair(tickets, now, properties)

        assertEquals(1, pairs.size)
        assertEquals(1L, pairs[0].first.userId)
        assertEquals(2L, pairs[0].second.userId)
    }

    @Test
    fun `갓 들어온 유저끼리 MMR 차이가 크면 매칭하지 않는다`() {
        val tickets = listOf(ticket(1, 1000, 0), ticket(2, 2000, 0))

        assertTrue(MatchSweep.pair(tickets, now, properties).isEmpty())
    }

    @Test
    fun `기다린 만큼 허용 MMR 차이가 넓어진다`() {
        // 30초 대기 -> 100 + 30 * 20 = 700 까지 허용
        val tickets = listOf(ticket(1, 1000, 30), ticket(2, 1600, 0))

        assertEquals(1, MatchSweep.pair(tickets, now, properties).size)
    }

    @Test
    fun `최대 대기를 넘기면 MMR과 무관하게 매칭된다`() {
        val tickets = listOf(ticket(1, 0, 120), ticket(2, 999_999, 0))

        assertEquals(1, MatchSweep.pair(tickets, now, properties).size)
    }

    @Test
    fun `한 유저는 한 쌍에만 들어간다`() {
        val tickets = listOf(
            ticket(1, 1000, 0),
            ticket(2, 1010, 0),
            ticket(3, 1020, 0),
            ticket(4, 1030, 0),
            ticket(5, 1040, 0),
        )

        val pairs = MatchSweep.pair(tickets, now, properties)

        assertEquals(2, pairs.size)
        val matchedUserIds = pairs.flatMap { listOf(it.first.userId, it.second.userId) }
        assertEquals(matchedUserIds.size, matchedUserIds.toSet().size)
    }

    @Test
    fun `대기열이 한 명이면 매칭이 없다`() {
        assertTrue(MatchSweep.pair(listOf(ticket(1, 1000, 999)), now, properties).isEmpty())
    }
}
