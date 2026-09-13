/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Public class Fact used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Fact extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_name]}}")
    private String name;
    @SchemaDescription("{{[rel_fact]}}")
    private String fact;
    @SchemaDescription("{{[rel_valid_since]}}")
    private String validSince;
    @SchemaDescription("{{[rel_valid_until]}}")
    private String validUntil;
    @SchemaDescription("{{[rel_source_id]}}")
    private int sourceId;
    @SchemaDescription("{{[rel_target_id]}}")
    private int targetId;
}
