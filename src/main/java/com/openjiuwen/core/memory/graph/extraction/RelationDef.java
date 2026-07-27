/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base relation type.
 */
@Data
public class RelationDef {
    private static final Map<String, String> RELATION_DEFINITION_DESCRIPTION = new LinkedHashMap<>();

    private String name = "Relation";
    private Map<String, String> description = RELATION_DEFINITION_DESCRIPTION;
    private Class<? extends EntityDef> lhs = EntityDef.class;
    private Class<? extends EntityDef> rhs = EntityDef.class;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void registerDescription(String language, String description) {
        RELATION_DEFINITION_DESCRIPTION.put(language, description);
    }
}
