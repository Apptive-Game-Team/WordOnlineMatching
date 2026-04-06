package com.wordonline.matching.data.dto;

import com.wordonline.matching.magic.dto.MagicDto;

import java.util.List;

/**
 * Full game config returned by GET /api/data/config.
 * parameters is a list of {group, key, value} tuples so that multi-word
 * game object names (e.g. "fire_spirit") remain unambiguous after the client
 * rebuilds the nested Dictionary&lt;string, Dictionary&lt;string, Fix64&gt;&gt;.
 * Unity's JsonUtility cannot deserialize Dictionary but can deserialize
 * List&lt;[Serializable]&gt;.
 */
public record GameConfigResponse(
        String version,
        List<MagicDto> magicRecipes,
        List<ParameterEntryDto> parameters
) {
}
