package com.wordonline.matching.matching.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.wordonline.matching.matching.config.MatchTicketProperties
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.MatchTicketState
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant

@Repository
class MatchTicketRepository(
    private val redis: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: MatchTicketProperties,
) {
    private val clock = Clock.systemUTC()
    companion object {
        private const val QUEUE_KEY = "matching:queue"
        private const val ACTIVE_PREFIX = "matching:active:"
        private const val TICKET_PREFIX = "matching:ticket:"
        private const val EVENT_PREFIX = "matching:events:"
        private const val LEASE_KEY = "matching:allocation-leases"
    }

    private val enqueueScript = DefaultRedisScript(ENQUEUE_SCRIPT, String::class.java)
    private val claimScript = DefaultRedisScript(CLAIM_SCRIPT, Long::class.java)
    private val transitionScript = DefaultRedisScript(TRANSITION_SCRIPT, String::class.java)
    private val transitionPairScript = DefaultRedisScript(TRANSITION_PAIR_SCRIPT, Long::class.java)
    private val cancelScript = DefaultRedisScript(CANCEL_SCRIPT, String::class.java)

    suspend fun enqueue(ticket: MatchTicket): MatchTicket {
        val json = encode(ticket)
        val result = redis.execute(
            enqueueScript,
            listOf(QUEUE_KEY, activeKey(ticket.userId), ticketKey(ticket.ticketId), eventKey(ticket.userId)),
            listOf(json, ticket.userId.toString(), ticket.updatedAt.toEpochMilli().toString()),
        ).next().awaitSingle()
        return decode(result)
    }

    suspend fun getActive(userId: Long): MatchTicket? {
        val ticketId = redis.opsForValue().get(activeKey(userId)).awaitSingleOrNull() ?: return null
        return redis.opsForValue().get(ticketKey(ticketId)).awaitSingleOrNull()?.let(::decode)
    }

    suspend fun claim(first: MatchTicket, second: MatchTicket): Boolean {
        val result = redis.execute(
            claimScript,
            listOf(QUEUE_KEY, ticketKey(first.ticketId), ticketKey(second.ticketId), eventKey(first.userId), eventKey(second.userId), LEASE_KEY),
            listOf(encode(first), encode(second), first.userId.toString(), second.userId.toString(), first.allocationLeaseUntil!!.toEpochMilli().toString()),
        ).next().awaitSingle()
        return result == 1L
    }

    suspend fun transition(ticket: MatchTicket, expected: MatchTicketState): MatchTicket? {
        val result = redis.execute(
            transitionScript,
            listOf(ticketKey(ticket.ticketId), eventKey(ticket.userId), LEASE_KEY, QUEUE_KEY),
            listOf(expected.name, encode(ticket), ticket.ticketId, properties.terminalTtl.toMillis().toString(), ticket.state.name, ticket.userId.toString(), ticket.updatedAt.toEpochMilli().toString()),
        ).next().awaitSingleOrNull()
        return result?.takeIf { it.isNotEmpty() }?.let(::decode)
    }

    suspend fun transitionPair(first: MatchTicket, second: MatchTicket, expected: MatchTicketState): Boolean {
        val result = redis.execute(
            transitionPairScript,
            listOf(ticketKey(first.ticketId), ticketKey(second.ticketId), eventKey(first.userId), eventKey(second.userId), LEASE_KEY),
            listOf(expected.name, encode(first), encode(second), first.ticketId, second.ticketId, properties.terminalTtl.toMillis().toString()),
        ).next().awaitSingle()
        return result == 1L
    }

    suspend fun cancel(userId: Long, ticketId: String): MatchTicket? {
        val result = redis.execute(
            cancelScript,
            listOf(activeKey(userId), QUEUE_KEY, eventKey(userId), LEASE_KEY),
            listOf(TICKET_PREFIX, userId.toString(), Instant.now(clock).toString(), properties.terminalTtl.toMillis().toString(), ticketId),
        ).next().awaitSingleOrNull()
        return result?.takeIf { it.isNotEmpty() }?.let(::decode)
    }

    suspend fun findCandidates(limit: Long = 50): List<MatchTicket> =
        redis.opsForZSet().range(QUEUE_KEY, Range.closed(0L, limit - 1)).collectList().awaitSingle()
            .mapNotNull { getActive(it.toLong()) }
            .filter { it.state == MatchTicketState.QUEUED }

    suspend fun size(): Long = redis.opsForZSet().size(QUEUE_KEY).awaitSingle()

    suspend fun expiredAllocations(): List<MatchTicket> {
        val now = Instant.now(clock).toEpochMilli().toDouble()
        val ids = redis.opsForZSet().rangeByScore(LEASE_KEY, Range.closed(Double.NEGATIVE_INFINITY, now)).collectList().awaitSingle()
        return ids.mapNotNull { id -> redis.opsForValue().get(ticketKey(id)).awaitSingleOrNull()?.let(::decode) }
    }

    suspend fun expiredQueued(): List<MatchTicket> {
        val cutoff = Instant.now(clock).minus(properties.queueTimeout).toEpochMilli().toDouble()
        val ids = redis.opsForZSet().rangeByScore(QUEUE_KEY, Range.closed(Double.NEGATIVE_INFINITY, cutoff)).collectList().awaitSingle()
        return ids.mapNotNull { getActive(it.toLong()) }.filter { it.state == MatchTicketState.QUEUED }
    }

    fun events(userId: Long) = redis.listenToChannel(eventKey(userId))
        .map { decode(it.message) }
        .asFlow()

    private fun encode(ticket: MatchTicket): String = objectMapper.writeValueAsString(ticket)
    private fun decode(json: String): MatchTicket = objectMapper.readValue(json, MatchTicket::class.java)
    private fun activeKey(userId: Long) = "$ACTIVE_PREFIX$userId"
    private fun ticketKey(ticketId: String) = "$TICKET_PREFIX$ticketId"
    private fun eventKey(userId: Long) = "$EVENT_PREFIX$userId"

}

private const val ENQUEUE_SCRIPT = """
local active = redis.call('GET', KEYS[2])
if active then
  local current = redis.call('GET', 'matching:ticket:' .. active)
  if current then
    local state = cjson.decode(current).state
    if state == 'QUEUED' or state == 'ALLOCATING' or state == 'MATCHED' then return current end
  end
end
redis.call('SET', KEYS[3], ARGV[1])
redis.call('SET', KEYS[2], cjson.decode(ARGV[1]).ticketId)
redis.call('ZADD', KEYS[1], tonumber(ARGV[3]), ARGV[2])
redis.call('PUBLISH', KEYS[4], ARGV[1])
return ARGV[1]
"""

private const val CLAIM_SCRIPT = """
local first = redis.call('GET', KEYS[2]); local second = redis.call('GET', KEYS[3])
if not first or not second or cjson.decode(first).state ~= 'QUEUED' or cjson.decode(second).state ~= 'QUEUED' then return 0 end
redis.call('SET', KEYS[2], ARGV[1]); redis.call('SET', KEYS[3], ARGV[2])
redis.call('ZREM', KEYS[1], ARGV[3], ARGV[4])
redis.call('ZADD', KEYS[6], ARGV[5], cjson.decode(ARGV[1]).ticketId, ARGV[5], cjson.decode(ARGV[2]).ticketId)
redis.call('PUBLISH', KEYS[4], ARGV[1]); redis.call('PUBLISH', KEYS[5], ARGV[2])
return 1
"""

private const val TRANSITION_SCRIPT = """
local current = redis.call('GET', KEYS[1])
if not current or cjson.decode(current).state ~= ARGV[1] then return '' end
redis.call('SET', KEYS[1], ARGV[2]); redis.call('ZREM', KEYS[3], ARGV[3])
redis.call('PUBLISH', KEYS[2], ARGV[2])
if ARGV[5] ~= 'QUEUED' and ARGV[5] ~= 'ALLOCATING' then redis.call('ZREM', KEYS[4], ARGV[6]); redis.call('PEXPIRE', KEYS[1], ARGV[4]) end
if ARGV[5] == 'QUEUED' then redis.call('ZADD', KEYS[4], ARGV[7], ARGV[6]) end
return ARGV[2]
"""

private const val CANCEL_SCRIPT = """
local id = redis.call('GET', KEYS[1]); if not id then return '' end
if id ~= ARGV[5] then return '' end
local key = ARGV[1] .. id; local raw = redis.call('GET', key); if not raw then return '' end
local ticket = cjson.decode(raw)
if ticket.state == 'QUEUED' then
 ticket.state = 'CANCELED'; ticket.reason = 'USER_REQUESTED'; ticket.version = ticket.version + 1; ticket.updatedAt = ARGV[3]
 local updated = cjson.encode(ticket); redis.call('SET', key, updated); redis.call('ZREM', KEYS[2], ARGV[2]); redis.call('ZREM', KEYS[4], id)
 redis.call('PUBLISH', KEYS[3], updated); redis.call('PEXPIRE', key, ARGV[4]); return updated
end
return raw
"""

private const val TRANSITION_PAIR_SCRIPT = """
local first = redis.call('GET', KEYS[1]); local second = redis.call('GET', KEYS[2])
if not first or not second or cjson.decode(first).state ~= ARGV[1] or cjson.decode(second).state ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[6]); redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[6])
redis.call('ZREM', KEYS[5], ARGV[4], ARGV[5])
redis.call('PUBLISH', KEYS[3], ARGV[2]); redis.call('PUBLISH', KEYS[4], ARGV[3])
return 1
"""
