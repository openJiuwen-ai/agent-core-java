/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base entity type.
 * 
 * @since 0.1.7
 */
@Data
public class EntityDef {
    private static final Map<String, String> ENTITY_DEFINITION_DESCRIPTION = new LinkedHashMap<>();

    private String name = "Entity";
    private Map<String, String> description = ENTITY_DEFINITION_DESCRIPTION;

    /**
     * EntityDefAttr.
     * 
     * @since 0.1.7
     */
    private EntityDefAttr attributes = new EntityDefAttr();

    /**
     * registerDescription.
     * 
     * @param language language
     * @param description description
     * @since 0.1.7
     */
    public static void registerDescription(String language, String description) {
        ENTITY_DEFINITION_DESCRIPTION.put(language, description);
    }

    /**
     * entityDefinitionDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected static Map<String, String> entityDefinitionDescription() {
        return ENTITY_DEFINITION_DESCRIPTION;
    }
}
