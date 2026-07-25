/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base entity type.
 */
@Data
public class EntityDef {
    private static final Map<String, String> ENTITY_DEFINITION_DESCRIPTION = new LinkedHashMap<>();

    private String name = "Entity";
    private Map<String, String> description = ENTITY_DEFINITION_DESCRIPTION;
    private EntityDefAttr attributes = new EntityDefAttr();

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void registerDescription(String language, String description) {
        ENTITY_DEFINITION_DESCRIPTION.put(language, description);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static Map<String, String> entityDefinitionDescription() {
        return ENTITY_DEFINITION_DESCRIPTION;
    }
}
