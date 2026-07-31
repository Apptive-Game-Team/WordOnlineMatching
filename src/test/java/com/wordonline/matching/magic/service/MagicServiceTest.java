package com.wordonline.matching.magic.service;

import com.wordonline.matching.magic.domain.UserMagic;
import com.wordonline.matching.magic.repository.UserMagicRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MagicServiceTest {

    @Mock
    private UserMagicRepository userMagicRepository;

    @InjectMocks
    private MagicService magicService;

    @Test
    @DisplayName("마법_지급시_유저와_마법_식별자가_뒤바뀌지_않고_저장됨")
    void giveMagic_SavesUserIdAndMagicIdInCorrectFields() {
        when(userMagicRepository.save(any(UserMagic.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(magicService.giveMagic(7L, 3L))
                .verifyComplete();

        ArgumentCaptor<UserMagic> captor = ArgumentCaptor.forClass(UserMagic.class);
        verify(userMagicRepository).save(captor.capture());
        UserMagic saved = captor.getValue();
        assert saved.getUserId().equals(7L);
        assert saved.getMagicId().equals(3L);
    }
}
