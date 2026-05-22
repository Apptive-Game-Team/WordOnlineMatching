package com.wordonline.matching.magic.dto;

import java.util.List;

public record MagicsResponse(
        String version,
        List<MagicDto> magics,
        boolean changed
) {
}
