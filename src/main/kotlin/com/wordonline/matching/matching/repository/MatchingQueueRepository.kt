package com.wordonline.matching.matching.repository

import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class MatchingQueueRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
) {
    companion object {
        private const val QUEUE_KEY = "matching:queue"
        private const val COUNTER_KEY = "matching:session-counter"
        private const val TIMEOUT_MS = 30_000L
    }

    fun enqueue(userId: Long): Mono<Void> =
        redisTemplate.opsForZSet()
            .add(QUEUE_KEY, userId.toString(), System.currentTimeMillis().toDouble())
            .then()

    fun isInQueue(userId: Long): Mono<Boolean> =
        redisTemplate.opsForZSet()
            .score(QUEUE_KEY, userId.toString() as Any)
            .map { true }
            .defaultIfEmpty(false)

    fun dequeuePair(): Mono<List<Long>> =
        redisTemplate.opsForZSet().popMin(QUEUE_KEY, 2)
            .collectList()
            .flatMap { tuples ->
                if (tuples.size < 2) {
                    // put the single popped entry back with original score
                    val requeue = tuples.map { t ->
                        redisTemplate.opsForZSet().add(QUEUE_KEY, t.value!!, t.score!!)
                    }
                    Mono.`when`(requeue).thenReturn(emptyList())
                } else {
                    Mono.just(tuples.map { it.value!!.toLong() })
                }
            }

    fun remove(userId: Long): Mono<Long> =
        redisTemplate.opsForZSet().remove(QUEUE_KEY, userId.toString() as Any)

    fun size(): Mono<Long> =
        redisTemplate.opsForZSet().size(QUEUE_KEY)

    fun nextSessionId(): Mono<Long> =
        redisTemplate.opsForValue().increment(COUNTER_KEY)

    /** Remove entries that have been in queue longer than [TIMEOUT_MS] and return their user IDs. */
    fun removeExpired(): Flux<Long> {
        val expiredBefore = (System.currentTimeMillis() - TIMEOUT_MS).toDouble()
        val range = Range.closed(Double.NEGATIVE_INFINITY, expiredBefore)

        return redisTemplate.opsForZSet().rangeByScore(QUEUE_KEY, range)
            .map { it.toLong() }
            .collectList()
            .flatMapMany { expiredIds ->
                if (expiredIds.isEmpty()) return@flatMapMany Flux.empty()
                redisTemplate.opsForZSet().removeRangeByScore(QUEUE_KEY, range)
                    .thenMany(Flux.fromIterable(expiredIds))
            }
    }
}
