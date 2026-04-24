package com.wordonline.matching.matching.repository

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class MatchingQueueRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
) {
    companion object {
        private const val QUEUE_KEY = "matching:queue"
        private const val COUNTER_KEY = "matching:session-counter"
    }

    fun enqueue(userId: Long): Mono<Boolean> =
        redisTemplate.opsForSet().add(QUEUE_KEY, userId.toString()).map { it > 0 }

    fun isInQueue(userId: Long): Mono<Boolean> =
        redisTemplate.opsForSet().isMember(QUEUE_KEY, userId.toString() as Any)

    fun dequeuePair(): Mono<List<Long>> =
        redisTemplate.opsForSet().pop(QUEUE_KEY, 2)
            .map { it.toLong() }
            .collectList()
            .flatMap { list ->
                if (list.size < 2) {
                    Mono.`when`(list.map { enqueue(it) }).thenReturn(emptyList())
                } else {
                    Mono.just(list)
                }
            }

    fun remove(userId: Long): Mono<Long> =
        redisTemplate.opsForSet().remove(QUEUE_KEY, userId.toString() as Any)

    fun size(): Mono<Long> =
        redisTemplate.opsForSet().size(QUEUE_KEY)

    fun nextSessionId(): Mono<Long> =
        redisTemplate.opsForValue().increment(COUNTER_KEY)
}
