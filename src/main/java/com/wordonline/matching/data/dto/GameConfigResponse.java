package com.wordonline.matching.data.dto;

import com.wordonline.matching.data.domain.Parameter;
import com.wordonline.matching.magic.dto.MagicDto;

import java.util.List;

public record GameConfigResponse(
        String version,
        List<MagicDto> magicRecipes,
        List<Parameter> parameters
) {
}
