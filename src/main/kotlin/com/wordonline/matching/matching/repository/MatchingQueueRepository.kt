package com.wordonline.matching.matching.repository

import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class MatchingQueueRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
) {
    companion object {
        private const val QUEUE_KEY = "matching:queue"
        private const val MMR_KEY = "matching:mmr"
        private const val COUNTER_KEY = "matching:session-counter"
        private const val TIMEOUT_MS = 30_000L
        private val REMOVE_EXPIRED_SCRIPT: RedisScript<List<*>> = RedisScript.of(
            """
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local removed = {}
            for _, member in ipairs(expired) do
                redis.call('ZREM', KEYS[1], member)
                redis.call('HDEL', KEYS[2], member)
                table.insert(removed, member)
            end
            return removed
            """.trimIndent(),
            List::class.java
        ) as RedisScript<List<*>>
    }

    fun enqueue(userId: Long, mmr: Long): Mono<Void> =
        redisTemplate.opsForZSet()
            .add(QUEUE_KEY, userId.toString(), System.currentTimeMillis().toDouble())
            .then(redisTemplate.opsForHash<String, String>().put(MMR_KEY, userId.toString(), mmr.toString()))
            .then()

    fun isInQueue(userId: Long): Mono<Boolean> =
        redisTemplate.opsForZSet()
            .score(QUEUE_KEY, userId.toString() as Any)
            .map { it > expiredBefore() }
            .defaultIfEmpty(false)

    fun dequeueBestPair(): Mono<List<Long>> =
        redisTemplate.opsForZSet().range(QUEUE_KEY, Range.closed(0L, -1L))
            .collectList()
            .flatMap { userIds ->
                if (userIds.size < 2) return@flatMap Mono.just(emptyList())
                redisTemplate.opsForHash<String, String>().multiGet(MMR_KEY, userIds)
                    .flatMap { mmrValues ->
                        val candidates = userIds.zip(mmrValues)
                            .map { (id, mmr) -> id.toLong() to (mmr?.toLongOrNull() ?: 0L) }
                            .sortedBy { it.second }

                        val (uid1, uid2) = findClosestPair(candidates)

                        Mono.`when`(
                            redisTemplate.opsForZSet().remove(QUEUE_KEY, uid1.toString(), uid2.toString()),
                            redisTemplate.opsForHash<String, String>().remove(MMR_KEY, uid1.toString(), uid2.toString()),
                        ).thenReturn(listOf(uid1, uid2))
                    }
            }

    fun remove(userId: Long): Mono<Void> =
        redisTemplate.opsForZSet().remove(QUEUE_KEY, userId.toString() as Any)
            .then(redisTemplate.opsForHash<String, String>().remove(MMR_KEY, userId.toString() as Any))
            .then()

    fun size(): Mono<Long> =
        redisTemplate.opsForZSet().size(QUEUE_KEY)

    fun nextSessionId(): Mono<Long> =
        redisTemplate.opsForValue().increment(COUNTER_KEY)

    fun removeExpired(): Flux<Long> {
        val expiredBefore = expiredBefore()

        return redisTemplate.execute(
            REMOVE_EXPIRED_SCRIPT,
            listOf(QUEUE_KEY, MMR_KEY),
            listOf(expiredBefore.toString()),
        )
            .flatMapIterable { it }
            .map { it.toString().toLong() }
    }

    private fun expiredBefore(): Double =
        (System.currentTimeMillis() - TIMEOUT_MS).toDouble()

    private fun findClosestPair(sorted: List<Pair<Long, Long>>): Pair<Long, Long> {
        var minDiff = Long.MAX_VALUE
        var best = sorted[0].first to sorted[1].first
        for (i in 0 until sorted.size - 1) {
            val diff = sorted[i + 1].second - sorted[i].second
            if (diff < minDiff) {
                minDiff = diff
                best = sorted[i].first to sorted[i + 1].first
            }
        }
        return best
    }
}
