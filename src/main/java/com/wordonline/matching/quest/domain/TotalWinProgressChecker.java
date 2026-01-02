package com.wordonline.matching.quest.domain;

import org.springframework.stereotype.Component;

import com.wordonline.matching.auth.domain.User;
import com.wordonline.matching.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component("total_win_pc")
@RequiredArgsConstructor
public class TotalWinProgressChecker implements ProgressChecker {

    private final UserRepository userRepository;

    @Override
    public Mono<Integer> check(long userId) {
        return userRepository.findById(userId)
                .map(User::getTotalWins);
    }
}
