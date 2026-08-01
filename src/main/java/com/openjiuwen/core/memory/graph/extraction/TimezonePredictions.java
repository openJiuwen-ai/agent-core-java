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
 * Public class TimezonePredictions used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TimezonePredictions extends MultilingualBaseModel {
    @SchemaDescription("{{[tz_list]}}")
    private List<PossibleTimezone> extractedRelations;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("extractedRelations", Map.of(
                "type", "array",
                "items", Map.of("$ref", "#/$defs/PossibleTimezone")
        ));
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("extractedRelations"));
        return schema;
    }
}
