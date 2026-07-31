package com.wordonline.matching.matching.repository

import com.wordonline.matching.matching.domain.MatchTicket
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface MatchTicketRepository : R2dbcRepository<MatchTicket, Long> {

    /**
     * Enqueue or refresh a ticket.
     * Re-requesting a match while already queued keeps the original `enqueued_at`, so it cannot
     * reset the wait time the tolerance calculation depends on. Coming from any other state
     * starts a fresh wait.
     */
    @Query(
        """
        INSERT INTO match_tickets (user_id, mmr, state, enqueued_at, updated_at)
        VALUES (:userId, :mmr, 'QUEUED', now(), now())
        ON CONFLICT (user_id) DO UPDATE
        SET mmr = EXCLUDED.mmr,
            state = 'QUEUED',
            session_id = NULL,
            server_url = NULL,
            left_user_id = NULL,
            right_user_id = NULL,
            retry_count = 0,
            enqueued_at = CASE
                WHEN match_tickets.state = 'QUEUED' THEN match_tickets.enqueued_at
                ELSE now()
            END,
            updated_at = now()
        """
    )
    fun enqueue(@Param("userId") userId: Long, @Param("mmr") mmr: Long): Mono<Long>

    /**
     * Take a batch of queued tickets for this tick. Rows locked by another lobby instance are
     * skipped, so every instance works on a disjoint slice and no user can be claimed twice.
     * Must run inside a transaction.
     */
    @Query(
        """
        SELECT * FROM match_tickets
        WHERE state = 'QUEUED'
        ORDER BY mmr
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """
    )
    fun claimQueued(@Param("batchSize") batchSize: Int): Flux<MatchTicket>

    @Query(
        """
        UPDATE match_tickets
        SET state = 'MATCHED',
            session_id = :sessionId,
            left_user_id = :leftUserId,
            right_user_id = :rightUserId,
            updated_at = now()
        WHERE user_id = :userId AND state = 'QUEUED'
        """
    )
    fun markMatched(
        @Param("userId") userId: Long,
        @Param("sessionId") sessionId: String,
        @Param("leftUserId") leftUserId: Long,
        @Param("rightUserId") rightUserId: Long,
    ): Mono<Long>

    @Query(
        """
        INSERT INTO match_tickets (user_id, state, session_id, server_url, left_user_id, right_user_id, updated_at)
        VALUES (:userId, 'PLAYING', :sessionId, :serverUrl, :leftUserId, :rightUserId, now())
        ON CONFLICT (user_id) DO UPDATE
        SET state = 'PLAYING',
            session_id = EXCLUDED.session_id,
            server_url = EXCLUDED.server_url,
            left_user_id = EXCLUDED.left_user_id,
            right_user_id = EXCLUDED.right_user_id,
            retry_count = 0,
            updated_at = now()
        """
    )
    fun markPlaying(
        @Param("userId") userId: Long,
        @Param("sessionId") sessionId: String,
        @Param("serverUrl") serverUrl: String,
        @Param("leftUserId") leftUserId: Long,
        @Param("rightUserId") rightUserId: Long,
    ): Mono<Long>

    /** PVE variant: there is no right-hand participant. */
    @Query(
        """
        INSERT INTO match_tickets (user_id, state, session_id, server_url, left_user_id, right_user_id, updated_at)
        VALUES (:userId, 'PLAYING', :sessionId, :serverUrl, :leftUserId, NULL, now())
        ON CONFLICT (user_id) DO UPDATE
        SET state = 'PLAYING',
            session_id = EXCLUDED.session_id,
            server_url = EXCLUDED.server_url,
            left_user_id = EXCLUDED.left_user_id,
            right_user_id = NULL,
            retry_count = 0,
            updated_at = now()
        """
    )
    fun markPlayingSolo(
        @Param("userId") userId: Long,
        @Param("sessionId") sessionId: String,
        @Param("serverUrl") serverUrl: String,
        @Param("leftUserId") leftUserId: Long,
    ): Mono<Long>

    /**
     * Put a matched user back in the queue after a failed session creation.
     * `enqueued_at` is preserved, so the wait already served still counts.
     * Returns 0 when the retry budget is spent.
     */
    @Query(
        """
        UPDATE match_tickets
        SET state = 'QUEUED',
            session_id = NULL,
            left_user_id = NULL,
            right_user_id = NULL,
            retry_count = retry_count + 1,
            updated_at = now()
        WHERE user_id = :userId AND state = 'MATCHED' AND retry_count < :maxRetry
        """
    )
    fun requeue(@Param("userId") userId: Long, @Param("maxRetry") maxRetry: Int): Mono<Long>

    /** Cancel only removes a queued user; someone already in a session keeps their ticket. */
    @Query("DELETE FROM match_tickets WHERE user_id = :userId AND state = 'QUEUED'")
    fun deleteQueued(@Param("userId") userId: Long): Mono<Long>

    @Query(
        """
        DELETE FROM match_tickets
        WHERE state = 'QUEUED'
          AND enqueued_at < now() - (CAST(:seconds AS INT) * INTERVAL '1 second')
        """
    )
    fun deleteExpiredQueued(@Param("seconds") seconds: Int): Mono<Long>

    /** Recover tickets stuck in MATCHED because the instance died mid session creation. */
    @Query(
        """
        UPDATE match_tickets
        SET state = 'QUEUED',
            session_id = NULL,
            left_user_id = NULL,
            right_user_id = NULL,
            updated_at = now()
        WHERE state = 'MATCHED'
          AND updated_at < now() - (CAST(:seconds AS INT) * INTERVAL '1 second')
        """
    )
    fun recoverStuckMatched(@Param("seconds") seconds: Int): Mono<Long>

    @Query(
        """
        SELECT user_id FROM match_tickets
        WHERE state = 'PLAYING'
          AND updated_at < now() - (CAST(:seconds AS INT) * INTERVAL '1 second')
        """
    )
    fun findPlayingOlderThan(@Param("seconds") seconds: Int): Flux<Long>

    @Query("SELECT count(*) FROM match_tickets WHERE state = 'QUEUED'")
    fun countQueued(): Mono<Long>

    @Query("SELECT nextval('match_session_seq')")
    fun nextSessionId(): Mono<Long>
}
