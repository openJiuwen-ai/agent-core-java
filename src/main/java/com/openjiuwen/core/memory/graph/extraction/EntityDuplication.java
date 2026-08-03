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
 * Public class EntityDuplication used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityDuplication extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_dupe_list]}}")
    private List<Duplication> duplicatedEntities;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> dupeList = new LinkedHashMap<>();
        dupeList.put("type", "array");
        dupeList.put("description", "{{[ent_dupe_list]}}");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("$ref", "#/$defs/Duplication");
        dupeList.put("items", items);
        properties.put("duplicatedEntities", dupeList);
        schema.put("properties", properties);
        return schema;
    }
}
