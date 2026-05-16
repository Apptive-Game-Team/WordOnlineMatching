package com.wordonline.matching.auth.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.matching.auth.dto.UserStatisticsGamesResponseDto;
import com.wordonline.matching.auth.dto.UserStatisticsOverviewResponseDto;
import com.wordonline.matching.auth.repository.UserStatisticsRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserStatisticsService {

    private final UserStatisticsRepository userStatisticsRepository;

    public Mono<UserStatisticsOverviewResponseDto> getOverview(Long userId) {
        return userStatisticsRepository.findOverviewByUserId(userId);
    }

    public Mono<UserStatisticsGamesResponseDto> getGames(Long userId, Pageable pageable) {
        return userStatisticsRepository.findGamesByUserId(userId, pageable)
                .collectList()
                .map(UserStatisticsGamesResponseDto::new);
    }
}
