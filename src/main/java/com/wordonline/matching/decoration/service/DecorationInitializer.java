package com.wordonline.matching.decoration.service;

import com.wordonline.matching.decoration.dto.DecorationRequest;
import com.wordonline.matching.decoration.entity.DecoType;
import com.wordonline.matching.decoration.entity.Decoration;
import com.wordonline.matching.decoration.entity.UserDecoration;
import com.wordonline.matching.decoration.repository.DecorationRepository;
import com.wordonline.matching.decoration.repository.UserDecorationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Deprecated
public class DecorationInitializer {

    private final UserDecorationRepository userDecorationRepository;

    public Mono<Void> initialize(long userId) {
        return giveInitialDecorations(userId);
    }

    private Mono<Void> giveInitialDecorations(long userId) {
        UserDecoration deco1 = new UserDecoration(null, userId, 1L, true);
        UserDecoration deco2 = new UserDecoration(null, userId, 2L, true);
        return userDecorationRepository.saveAll(List.of(deco1, deco2)).then();
    }
}