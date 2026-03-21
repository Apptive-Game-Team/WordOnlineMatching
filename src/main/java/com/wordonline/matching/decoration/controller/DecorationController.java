package com.wordonline.matching.decoration.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.decoration.dto.DecorationRequest;
import com.wordonline.matching.decoration.dto.DecorationsResponse;
import com.wordonline.matching.decoration.service.DecorationInitializer;
import com.wordonline.matching.decoration.service.DecorationService;
import com.wordonline.matching.quest.dto.QuestProgressResponseDto;
import com.wordonline.matching.quest.service.QuestService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
public class DecorationController {

    private final DecorationService decorationService;
    private final DecorationInitializer decorationInitializer;
    private final QuestService questService;

    @GetMapping("/mine/decorations")
    public Mono<DecorationsResponse> getMyDecoration(
            @UserId Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean equippedOnly
    ) {
        return getDecoration(userId, equippedOnly);
    }

    @GetMapping("/mine/decorations/{decoId}/quest-progress")
    public Mono<QuestProgressResponseDto> getQuestState(
            @UserId Long userId,
            @PathVariable Long decoId
    ) {
        return questService.findQuestProgressByDecoration(userId, decoId);
    }

    @PostMapping("/mine/decorations")
    public Mono<Void> setDecoration(
            @UserId Long userId,
            @RequestBody DecorationRequest decorationRequest
    ) {
        return decorationService.setDecoration(userId, decorationRequest);
    }

    @GetMapping("/{memberId}/decorations")
    public Mono<DecorationsResponse> getDecoration(
            @PathVariable long memberId,
            @RequestParam(required = false) boolean equippedOnly
    ) {
        return decorationService.getDecorationsByUserId(memberId, equippedOnly)
                .collectList()
                .map(DecorationsResponse::new);
    }
}
