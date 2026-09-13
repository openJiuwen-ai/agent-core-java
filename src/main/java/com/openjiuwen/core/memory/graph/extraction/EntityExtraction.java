/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Public class EntityExtraction used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityExtraction extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_ext_list]}}")
    private List<EntityDeclaration> extractedEntities;
}
