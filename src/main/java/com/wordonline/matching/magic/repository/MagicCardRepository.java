package com.wordonline.matching.magic.repository;

import com.wordonline.matching.magic.domain.MagicCard;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface MagicCardRepository extends R2dbcRepository<MagicCard, Long> {

    @Query("""
        SELECT mc.*
        FROM magic_cards mc
        WHERE mc.magic_id IN (
            SELECT DISTINCT magic_id FROM magic_cards WHERE updated_at > :timestamp
        )
    """)
    Flux<MagicCard> findAllByUpdatedMagicsSince(LocalDateTime timestamp);
}
