/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Base entity type's attributes.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class EntityDefAttr extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_summary]}}")
    private String content = "";
}
