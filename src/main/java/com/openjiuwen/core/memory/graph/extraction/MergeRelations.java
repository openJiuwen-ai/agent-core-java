/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.List;

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
}
