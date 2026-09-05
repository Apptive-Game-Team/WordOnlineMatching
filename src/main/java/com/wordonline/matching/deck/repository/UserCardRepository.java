package com.wordonline.matching.deck.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.deck.domain.UserCard;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserCardRepository extends R2dbcRepository<UserCard, Long> {

    Flux<UserCard> findAllByUserId(Long userId);

    @Query(
            """
            INSERT INTO user_cards(user_id, card_id, count)
            VALUES (:userId, :cardId, :count)
            ON CONFLICT (card_id, user_id)
            DO UPDATE SET count = user_cards.count + :count
            """
    )
    Mono<Void> addCount(Long userId, Long cardId, Integer count);
}
