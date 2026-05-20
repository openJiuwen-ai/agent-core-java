/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.Map;

/**
 * Public class EntitySummary used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
public class EntitySummary extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_summary]}}")
    private String summary;
    @SchemaDescription("{{[ent_attributes]}}")
    private Map<String, Object> attributes;
}
