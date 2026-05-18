package com.wordonline.matching.auth.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.wordonline.matching.auth.dto.UserGameRecordResponseDto;
import com.wordonline.matching.auth.dto.UserStatisticsOverviewResponseDto;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class UserStatisticsRepository {

    private final DatabaseClient databaseClient;

    public Mono<UserStatisticsOverviewResponseDto> findOverviewByUserId(Long userId) {
        return databaseClient.sql("""
                        SELECT
                            COUNT(*) AS total_game_num,
                            COALESCE(SUM(CASE WHEN win_user_id = :userId THEN 1 ELSE 0 END), 0) AS total_win_num
                        FROM statistic_games
                        WHERE win_user_id = :userId OR loss_user_id = :userId
                        """)
                .bind("userId", userId)
                .map((row, metadata) -> new UserStatisticsOverviewResponseDto(
                        row.get("total_game_num", Number.class).longValue(),
                        row.get("total_win_num", Number.class).longValue()))
                .one();
    }

    public Flux<UserGameRecordResponseDto> findGamesByUserId(Long userId, Pageable pageable) {
        int limit = pageable.isPaged() ? pageable.getPageSize() : Integer.MAX_VALUE;
        long offset = pageable.isPaged() ? pageable.getOffset() : 0L;

        return databaseClient.sql("""
                        SELECT
                            CASE
                                WHEN win_user_id = :userId THEN loss_user_id
                                ELSE win_user_id
                            END AS opponent_id,
                            CASE
                                WHEN win_user_id = :userId THEN 'win'
                                ELSE 'lose'
                            END AS result
                        FROM statistic_games
                        WHERE win_user_id = :userId OR loss_user_id = :userId
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> new UserGameRecordResponseDto(
                        row.get("opponent_id", Long.class),
                        row.get("result", String.class)))
                .all();
    }
}
