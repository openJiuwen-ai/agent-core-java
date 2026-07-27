/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base entity type's attributes.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityDefAttr extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_summary]}}")
    private String content = "";

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> contentDefinition = new LinkedHashMap<>();
        contentDefinition.put("type", "string");
        contentDefinition.put("description", "{{[ent_summary]}}");
        properties.put("content", contentDefinition);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
