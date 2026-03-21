package com.wordonline.matching.decoration.dto;

import java.util.List;

@Deprecated
public record DecorationsResponse(
        List<DecorationResponse> decorations
) {
    
}
