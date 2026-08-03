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
 * Public class RelevantFacts used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RelevantFacts extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_filter_reasoning]}}")
    private String briefReasoning;
    @SchemaDescription("{{[rel_filter_list]}}")
    private List<Integer> relevantRelations;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("brief_reasoning", Map.of("type", "string", "description", "{{[rel_filter_reasoning]}}"));
        properties.put("relevant_relations", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "{{[rel_filter_list]}}"));
        schema.put("properties", properties);
        schema.put("required", List.of("relevant_relations"));
        return schema;
    }
}
