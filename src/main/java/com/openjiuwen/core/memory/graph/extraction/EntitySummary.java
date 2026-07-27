/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class EntitySummary used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntitySummary extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_summary]}}")
    private String summary;
    @SchemaDescription("{{[ent_attributes]}}")
    private Map<String, Object> attributes;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> summaryProp = new LinkedHashMap<>();
        summaryProp.put("type", "string");
        summaryProp.put("description", "{{[ent_summary]}}");
        properties.put("summary", summaryProp);
        Map<String, Object> attrProp = new LinkedHashMap<>();
        attrProp.put("type", "object");
        attrProp.put("description", "{{[ent_attributes]}}");
        properties.put("attributes", attrProp);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", "EntitySummary");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.copyOf(properties.keySet()));
        return schema;
    }
}
