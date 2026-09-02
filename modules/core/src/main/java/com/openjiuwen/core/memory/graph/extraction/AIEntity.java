/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

/**
 * AI assistant entity type.
 * 
 * @since 0.1.7
 */
public class AIEntity extends EntityDef {
    /**
     * AIEntity.
     * 
     * @since 0.1.7
     */
    public AIEntity() {
        setName("AI");
        setDescription(entityDefinitionDescription());
    }
}
