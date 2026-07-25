/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class Fact used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Fact extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_name]}}")
    private String name;
    @SchemaDescription("{{[rel_fact]}}")
    private String fact;
    @SchemaDescription("{{[rel_valid_since]}}")
    private String validSince;
    @SchemaDescription("{{[rel_valid_until]}}")
    private String validUntil;
    @SchemaDescription("{{[rel_source_id]}}")
    private int sourceId;
    @SchemaDescription("{{[rel_target_id]}}")
    private int targetId;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", property("string", "{{[rel_name]}}"));
        properties.put("fact", property("string", "{{[rel_fact]}}"));
        properties.put("valid_since", property("string", "{{[rel_valid_since]}}"));
        properties.put("valid_until", property("string", "{{[rel_valid_until]}}"));
        properties.put("source_id", property("integer", "{{[rel_source_id]}}"));
        properties.put("target_id", property("integer", "{{[rel_target_id]}}"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", "Fact");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.copyOf(properties.keySet()));
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type);
        prop.put("description", description);
        return prop;
    }
}
