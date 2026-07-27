/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class EntityExtraction used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
public class EntityExtraction extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_ext_list]}}")
    private List<EntityDeclaration> extractedEntities;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> extractedEntitiesProp = new LinkedHashMap<>();
        extractedEntitiesProp.put("type", "array");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("$ref", "#/$defs/EntityDeclaration");
        extractedEntitiesProp.put("items", items);
        properties.put("extracted_entities", extractedEntitiesProp);
        schema.put("properties", properties);
        schema.put("required", List.of("extracted_entities"));
        Map<String, Object> defs = new LinkedHashMap<>();
        defs.put("EntityDeclaration", new EntityDeclaration().responseFormat());
        schema.put("$defs", defs);
        return schema;
    }
}
