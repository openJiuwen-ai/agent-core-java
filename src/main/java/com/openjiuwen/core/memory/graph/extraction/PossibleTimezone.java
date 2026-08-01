/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class PossibleTimezone used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PossibleTimezone extends MultilingualBaseModel {
    @SchemaDescription("{{[tz_name]}}")
    private String name;
    @SchemaDescription("{{[tz_offset]}}")
    private String offsetFromUtc;
    @SchemaDescription("{{[tz_reason]}}")
    private String reasoning;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string"));
        properties.put("offsetFromUtc", Map.of("type", "string"));
        properties.put("reasoning", Map.of("type", "string"));
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("name", "offsetFromUtc", "reasoning"));
        return schema;
    }
}
