package com.wordonline.matching.auth.dto;

import com.wordonline.matching.matching.dto.AccountMemberResponseDto;

public record UserDetailResponseDto(long id, String name, String email) {

    public UserDetailResponseDto(long id, AccountMemberResponseDto accountMemberResponseDto) {
        this(id, accountMemberResponseDto.getName(), accountMemberResponseDto.getEmail());
    }
}
