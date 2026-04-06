package com.wordonline.matching.data.dto;

/**
 * One balance parameter entry returned in GET /api/data/config.
 * Using a list of {group, key, value} tuples instead of a flat map so that
 * multi-word game object names (e.g. "fire_spirit", "healing_totem") remain
 * unambiguous — Unity's JsonUtility cannot deserialize Dictionary, but can
 * deserialize List&lt;GameParameterEntry&gt; when each entry is [Serializable].
 */
public record ParameterEntryDto(String group, String key, double value) {
}
