package com.wordonline.matching.magic.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Table(name = "user_magics")
@NoArgsConstructor
@AllArgsConstructor
public class UserMagic {

    @Id
    private Long id;
    private Long magicId;
    private Long userId;

    public UserMagic(Long magicId, Long userId) {
        this(null, magicId, userId);
    }
}
