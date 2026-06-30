/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

/**
 * Base entity type's attributes.
 */
@Data
public class EntityDefAttr extends MultilingualBaseModel {
    @SchemaDescription("{{[ent_summary]}}")
    private String content = "";
}
