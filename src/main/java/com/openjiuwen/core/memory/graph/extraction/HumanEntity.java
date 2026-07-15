/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

/**
 * Human entity type.
 * 
 * @since 0.1.7
 */
public class HumanEntity extends EntityDef {
    /**
     * HumanEntity.
     * 
     * @since 0.1.7
     */
    public HumanEntity() {
        setName("Human");
        setDescription(entityDefinitionDescription());
    }
}
