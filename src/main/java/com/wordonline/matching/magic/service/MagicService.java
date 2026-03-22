package com.wordonline.matching.magic.service;

import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.wordonline.matching.magic.domain.UserMagic;
import com.wordonline.matching.magic.dto.MagicResponse;
import com.wordonline.matching.magic.repository.UserMagicRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MagicService {

    private final UserMagicRepository userMagicRepository;

    public Mono<MagicResponse> findAll(long userId) {
        return userMagicRepository.findAllByUserId(userId)
                .map(UserMagic::getMagicId)
                .collectList()
                .map(MagicResponse::new);
    }

    public Mono<Void> giveMagic(long userId, long magicId) {
        return userMagicRepository.save(new UserMagic(userId, magicId))
                .then();
    }
}
