package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.config.MatchingProperties
import com.wordonline.matching.matching.domain.MatchTicket
import java.time.Duration
import java.time.Instant

/**
 * Pairing rule. Kept free of Spring and IO so it can be tested directly.
 *
 * Tickets arrive sorted by MMR, so a single adjacent-pair pass is enough: the closest possible
 * opponent for any ticket is one of its two neighbours. The tolerated gap grows with wait time,
 * which is what keeps a lonely MMR outlier from waiting forever.
 */
object MatchSweep {

    fun toleranceOf(ticket: MatchTicket, now: Instant, properties: MatchingProperties): Long {
        val waitSeconds = Duration.between(ticket.enqueuedAt, now).seconds.coerceAtLeast(0)
        if (waitSeconds >= properties.maxWaitSeconds) return Long.MAX_VALUE
        return properties.baseMmrDiff + waitSeconds * properties.widenPerSec
    }

    /** @param tickets queued tickets ordered by ascending MMR */
    fun pair(
        tickets: List<MatchTicket>,
        now: Instant,
        properties: MatchingProperties,
    ): List<Pair<MatchTicket, MatchTicket>> {
        val pairs = mutableListOf<Pair<MatchTicket, MatchTicket>>()
        var index = 0
        while (index < tickets.size - 1) {
            val left = tickets[index]
            val right = tickets[index + 1]
            val tolerance = maxOf(toleranceOf(left, now, properties), toleranceOf(right, now, properties))
            if (right.mmr - left.mmr <= tolerance) {
                pairs += left to right
                index += 2
            } else {
                index += 1
            }
        }
        return pairs
    }
}
