/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Public class EntityDeclaration used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityDeclaration extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_def_name]}}")
    private String name;
    @SchemaDescription("{{[ent_def_type]}}")
    private int entityTypeId;
}
