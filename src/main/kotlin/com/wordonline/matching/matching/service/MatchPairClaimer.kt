package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.config.MatchingProperties
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.repository.MatchTicketRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

data class ClaimedPair(val leftUserId: Long, val rightUserId: Long, val sessionId: String)

/**
 * Claims queued tickets and marks the resulting pairs MATCHED in one transaction.
 *
 * `SELECT ... FOR UPDATE SKIP LOCKED` is what makes this safe with several lobby instances:
 * each instance locks a disjoint slice, so no user can end up in two sessions and no application
 * level lock is needed. Session creation is deliberately left outside the transaction.
 */
@Component
class MatchPairClaimer(
    private val matchTicketRepository: MatchTicketRepository,
    private val properties: MatchingProperties,
) {

    @Transactional
    fun claimPairs(): Mono<List<ClaimedPair>> {
        val now = Instant.now()
        return matchTicketRepository.claimQueued(properties.batchSize)
            .collectList()
            .flatMap { tickets ->
                val pairs = MatchSweep.pair(tickets, now, properties)
                if (pairs.isEmpty()) {
                    Mono.just(emptyList())
                } else {
                    Flux.fromIterable(pairs)
                        .concatMap { (left, right) -> claim(left, right) }
                        .collectList()
                }
            }
    }

    private fun claim(left: MatchTicket, right: MatchTicket): Mono<ClaimedPair> =
        matchTicketRepository.nextSessionId().flatMap { sequence ->
            val sessionId = "session-$sequence"
            matchTicketRepository.markMatched(left.userId, sessionId, left.userId, right.userId)
                .then(matchTicketRepository.markMatched(right.userId, sessionId, left.userId, right.userId))
                .thenReturn(ClaimedPair(left.userId, right.userId, sessionId))
        }
}
