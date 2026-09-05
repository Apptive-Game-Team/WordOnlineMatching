package com.wordonline.matching.matching.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"rawtypes", "unchecked"})
class MatchingQueueRepositoryTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;
    @Mock
    private ReactiveHashOperations hashOperations;

    private MatchingQueueRepository repository;

    private void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        repository = new MatchingQueueRepository(redisTemplate);
    }

    private void stubTwoQueuedUsers() {
        when(zSetOperations.range(eq("matching:queue"), any(Range.class)))
                .thenReturn(Flux.just("1", "2"));
        when(hashOperations.multiGet(eq("matching:mmr"), anyList()))
                .thenReturn(Mono.just(List.of("100", "110")));
    }

    @Test
    void 쌍_점유에_성공하면_두_유저를_반환한다() {
        setUp();
        stubTwoQueuedUsers();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(repository.dequeueBestPair())
                .expectNext(List.of(1L, 2L))
                .verifyComplete();
    }

    @Test
    void 쌍_점유에_실패하면_빈_리스트를_반환한다() {
        setUp();
        stubTwoQueuedUsers();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(0L));

        StepVerifier.create(repository.dequeueBestPair())
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void 만료된_유저는_mmr_해시_필드도_함께_삭제된다() {
        setUp();
        when(zSetOperations.rangeByScore(eq("matching:queue"), any(Range.class)))
                .thenReturn(Flux.just("7", "8"));
        when(zSetOperations.removeRangeByScore(eq("matching:queue"), any(Range.class)))
                .thenReturn(Mono.just(2L));
        when(hashOperations.remove(eq("matching:mmr"), any(Object[].class)))
                .thenReturn(Mono.just(2L));

        StepVerifier.create(repository.removeExpired())
                .expectNext(7L, 8L)
                .verifyComplete();

        verify(hashOperations).remove("matching:mmr", "7", "8");
    }
}
