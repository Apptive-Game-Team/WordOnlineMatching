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

    public Mono<Void> giveDefaultMagics(long userId) {
        List<Long> defaultMagics = Stream.of(
                        LongStream.range(1, 25),
                        LongStream.range(33, 39),
                        LongStream.of(29, 32, 47, 28)
                )
                .flatMapToLong(s -> s)
                .boxed()
                .toList();

        return userMagicRepository.saveAll(
                defaultMagics.stream()
                        .map(magicId -> new UserMagic(magicId, userId))
                        .toList()
        ).then();
    }
}
