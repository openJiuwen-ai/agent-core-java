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
 * Public class RelationExtraction used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RelationExtraction extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_ext_list]}}")
    private List<Fact> extractedRelations;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> arrayProp = new LinkedHashMap<>();
        arrayProp.put("type", "array");
        arrayProp.put("description", "{{[rel_ext_list]}}");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("$ref", "#/$defs/Fact");
        arrayProp.put("items", items);
        properties.put("extracted_relations", arrayProp);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", "RelationExtraction");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }
}
