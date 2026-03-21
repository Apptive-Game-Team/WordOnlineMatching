package com.wordonline.matching.decoration.dto;

import com.wordonline.matching.decoration.entity.Decoration;

@Deprecated
public record DecorationResponse(
        long decorationId,
        String name,
        boolean isEquipped
) {
    public DecorationResponse(Decoration decoration, boolean isEquipped) {
        this(decoration.getId(), decoration.getName(), isEquipped);
    }
}

