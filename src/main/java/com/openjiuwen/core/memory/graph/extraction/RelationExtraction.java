/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.List;

/**
 * Public class RelationExtraction used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
public class RelationExtraction extends MultilingualBaseModel {
    @SchemaDescription("{{[rel_ext_list]}}")
    private List<Fact> extractedRelations;
}
