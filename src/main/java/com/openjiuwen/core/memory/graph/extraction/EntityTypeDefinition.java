/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entity and relation type definitions for graph extraction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.entity_type_definition} in
 * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
 */
public final class EntityTypeDefinition {

    public static final Map<String, String> ENTITY_DEFINITION_DESCRIPTION = new LinkedHashMap<>();
    public static final Map<String, String> RELATION_DEFINITION_DESCRIPTION = new LinkedHashMap<>();
    public static final Map<String, String> HUMAN_ENTITY_DESCRIPTION = new LinkedHashMap<>();
    public static final Map<String, String> AI_ENTITY_DESCRIPTION = new LinkedHashMap<>();

    private EntityTypeDefinition() {
    }

    /**
     * Base Entity Type's Attributes.
     * <p>
     * Mirrors Python's {@code EntityDefAttr} in
     * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
     */
    public static class EntityDefAttr extends MultilingualBaseModel {

        private String content = "";

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content == null ? "" : content;
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> contentDefinition = new LinkedHashMap<>();
            contentDefinition.put("type", "string");
            contentDefinition.put("description", "{{[ent_summary]}}");
            properties.put("content", contentDefinition);
            schema.put("properties", properties);
            return schema;
        }
    }

    /**
     * Base Entity Type.
     * <p>
     * Mirrors Python's {@code EntityDef} in
     * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
     */
    public static class EntityDef {

        private String name = "Entity";
        private Map<String, String> description = ENTITY_DEFINITION_DESCRIPTION;
        private MultilingualBaseModel attributes = new EntityDefAttr();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "Entity" : name;
        }

        public Map<String, String> getDescription() {
            return description;
        }

        public void setDescription(Map<String, String> description) {
            this.description = description == null ? new LinkedHashMap<>() : new LinkedHashMap<>(description);
        }

        public MultilingualBaseModel getAttributes() {
            return attributes;
        }

        public void setAttributes(MultilingualBaseModel attributes) {
            this.attributes = attributes == null ? new EntityDefAttr() : attributes;
        }
    }

    /**
     * Base Relation Type.
     * <p>
     * Mirrors Python's {@code RelationDef} in
     * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
     */
    public static class RelationDef {

        private String name = "Relation";
        private Map<String, String> description = RELATION_DEFINITION_DESCRIPTION;
        private Class<? extends EntityDef> lhs;
        private Class<? extends EntityDef> rhs;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "Relation" : name;
        }

        public Map<String, String> getDescription() {
            return description;
        }

        public void setDescription(Map<String, String> description) {
            this.description = description == null ? new LinkedHashMap<>() : new LinkedHashMap<>(description);
        }

        public Class<? extends EntityDef> getLhs() {
            return lhs;
        }

        public void setLhs(Class<? extends EntityDef> lhs) {
            this.lhs = lhs;
        }

        public Class<? extends EntityDef> getRhs() {
            return rhs;
        }

        public void setRhs(Class<? extends EntityDef> rhs) {
            this.rhs = rhs;
        }
    }

    /**
     * Human Entity Type.
     * <p>
     * Mirrors Python's {@code HumanEntity} in
     * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
     */
    public static class HumanEntity extends EntityDef {

        public HumanEntity() {
            setName("Human");
        }
    }

    /**
     * AI Assistant Entity Type.
     * <p>
     * Mirrors Python's {@code AIEntity} in
     * {@code openjiuwen/core/memory/graph/extraction/entity_type_definition.py}.
     */
    public static class AIEntity extends EntityDef {

        public AIEntity() {
            setName("AI");
        }
    }
}
