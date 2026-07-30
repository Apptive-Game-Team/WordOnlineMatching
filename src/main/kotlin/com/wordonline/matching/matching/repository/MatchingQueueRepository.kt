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
          private const val TIMEOUT_MS = 5 * 60 * 1000L

        // Claims both queue slots atomically: only removes them when both are still queued,
        // so two overlapping matching ticks can never hand out the same pair.
        private val CLAIM_PAIR_SCRIPT: RedisScript<Long> = RedisScript.of(
            """
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) and redis.call('ZSCORE', KEYS[1], ARGV[2]) then
                redis.call('ZREM', KEYS[1], ARGV[1], ARGV[2])
                redis.call('HDEL', KEYS[2], ARGV[1], ARGV[2])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
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

                        redisTemplate.execute(
                            CLAIM_PAIR_SCRIPT,
                            listOf(QUEUE_KEY, MMR_KEY),
                            listOf(uid1.toString(), uid2.toString()),
                        ).next()
                            .map { claimed -> if (claimed == 1L) listOf(uid1, uid2) else emptyList() }
                            .defaultIfEmpty(emptyList())
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
        val range = Range.closed(Double.NEGATIVE_INFINITY, expiredBefore)

        return redisTemplate.opsForZSet().rangeByScore(QUEUE_KEY, range)
            .map { it.toString().toLong() }
            .collectList()
            .flatMapMany { expiredIds ->
                if (expiredIds.isEmpty()) return@flatMapMany Flux.empty()
                val mmrFields = expiredIds.map { it.toString() as Any }.toTypedArray()
                redisTemplate.opsForZSet()
                    .removeRangeByScore(QUEUE_KEY, range)
                    .then(redisTemplate.opsForHash<String, String>().remove(MMR_KEY, *mmrFields))
                    .thenMany(Flux.fromIterable(expiredIds))
            }
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
