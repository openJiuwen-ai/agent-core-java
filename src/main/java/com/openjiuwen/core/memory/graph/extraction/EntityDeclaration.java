/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class EntityDeclaration used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityDeclaration extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_def_name]}}")
    private String name;
    @SchemaDescription("{{[ent_def_type]}}")
    private int entityTypeId;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "{{[ent_def_name]}}");
        properties.put("name", nameProp);
        Map<String, Object> typeProp = new LinkedHashMap<>();
        typeProp.put("type", "integer");
        typeProp.put("description", "{{[ent_def_type]}}");
        properties.put("entity_type_id", typeProp);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", "EntityDeclaration");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.copyOf(properties.keySet()));
        return schema;
    }
}
