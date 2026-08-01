/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class Duplication used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Duplication extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_dupe_name]}}")
    private String name;
    @SchemaDescription("{{[ent_dupe_id]}}")
    private int id;
    @SchemaDescription("{{[ent_dupe_id_list]}}")
    private List<Integer> duplicateIds;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string"));
        properties.put("id", Map.of("type", "integer"));
        Map<String, Object> duplicateIdsProp = new LinkedHashMap<>();
        duplicateIdsProp.put("type", "array");
        duplicateIdsProp.put("items", Map.of("type", "integer"));
        properties.put("duplicate_ids", duplicateIdsProp);
        schema.put("properties", properties);
        schema.put("required", List.of("name", "id", "duplicate_ids"));
        return schema;
    }
}
