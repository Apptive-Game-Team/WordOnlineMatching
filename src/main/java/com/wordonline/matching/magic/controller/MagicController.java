package com.wordonline.matching.magic.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.magic.dto.MagicResponse;
import com.wordonline.matching.magic.service.MagicService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
public class MagicController {

    private final MagicService magicService;

    @GetMapping("/mine/magics")
    public Mono<MagicResponse> getMyDecoration(
            @UserId Long userId
    ) {
        return magicService.findAll(userId);
    }
}
