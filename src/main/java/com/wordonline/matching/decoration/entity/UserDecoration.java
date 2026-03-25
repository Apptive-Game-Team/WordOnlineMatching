package com.wordonline.matching.decoration.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "user_decorations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class UserDecoration {

    @Id
    private Long id;

    private Long userId;
    private Long decorationId;
    private Boolean isEquipped = false;
}
