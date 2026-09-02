/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base relation type.
 * 
 * @since 0.1.7
 */
@Data
public class RelationDef {
    private static final Map<String, String> RELATION_DEFINITION_DESCRIPTION = new ConcurrentHashMap<>();

    private String name = "Relation";
    private Map<String, String> description = RELATION_DEFINITION_DESCRIPTION;
    private Class<? extends EntityDef> lhs = EntityDef.class;
    private Class<? extends EntityDef> rhs = EntityDef.class;

    /**
     * registerDescription.
     * 
     * @param language language
     * @param description description
     * @since 0.1.7
     */
    public static void registerDescription(String language, String description) {
        RELATION_DEFINITION_DESCRIPTION.put(language, description);
    }
}
