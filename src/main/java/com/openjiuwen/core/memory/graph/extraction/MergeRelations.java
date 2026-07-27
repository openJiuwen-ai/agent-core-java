/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class MergeRelations used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
public class MergeRelations extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_dupe_need_merge]}}")
    private boolean isNeedMerging;
    @SchemaDescription("{{[rel_dupe_reasoning]}}")
    private String shortReasoning;
    @SchemaDescription("{{[rel_dupe_content]}}")
    private String combinedContent;
    @SchemaDescription("{{[rel_dupe_id_list]}}")
    private List<Integer> duplicateIds;
    @SchemaDescription("{{[rel_valid_since]}}")
    private String validSince;
    @SchemaDescription("{{[rel_valid_until]}}")
    private String validUntil;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("is_need_merging", Map.of("type", "boolean", "description", "{{[rel_dupe_need_merge]}}"));
        properties.put("short_reasoning", Map.of("type", "string", "description", "{{[rel_dupe_reasoning]}}"));
        properties.put("combined_content", Map.of("type", "string", "description", "{{[rel_dupe_content]}}"));
        properties.put("duplicate_ids", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "{{[rel_dupe_id_list]}}"));
        properties.put("valid_since", Map.of("type", "string", "description", "{{[rel_valid_since]}}"));
        properties.put("valid_until", Map.of("type", "string", "description", "{{[rel_valid_until]}}"));
        schema.put("properties", properties);
        schema.put("required", List.of("is_need_merging", "duplicate_ids"));
        return schema;
    }
}
