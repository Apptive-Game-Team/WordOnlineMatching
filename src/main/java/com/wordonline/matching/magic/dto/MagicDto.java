package com.wordonline.matching.magic.dto;

import java.util.List;

public record MagicDto(
        Long id,
        String name,
        List<String> cards
) {
}
