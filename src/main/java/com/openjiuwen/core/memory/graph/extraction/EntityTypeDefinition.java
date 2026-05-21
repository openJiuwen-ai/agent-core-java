/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity and relation type definitions for graph extraction.
 * <p>
 * Mirrors Python's entity type definition classes from
 * <code>memory/graph/extraction/entity_type_definition.py</code>.
 */
public final class EntityTypeDefinition {

    private EntityTypeDefinition() {}

    /** Entity attribute definition. */
    public static class EntityDefAttr extends MultilingualBaseModel {
        private String content = "";

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            Map<String, String> contentDef = new HashMap<>();
            contentDef.put("type", "string");
            contentDef.put("description", "{{[ent_summary]}}");
            properties.put("content", contentDef);
            schema.put("properties", properties);
            return schema;
        }
    }

    /** Base entity type definition. */
    public static class EntityDef {
        private String name = "Entity";
        private Map<String, String> description = new HashMap<>();
        private EntityDefAttr attributes = new EntityDefAttr();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, String> getDescription() { return description; }
        public void setDescription(Map<String, String> description) { this.description = description; }
        public EntityDefAttr getAttributes() { return attributes; }
        public void setAttributes(EntityDefAttr attributes) { this.attributes = attributes; }
    }

    /** Base relation type definition. */
    public static class RelationDef {
        private String name = "Relation";
        private Map<String, String> description = new HashMap<>();
        private Class<? extends EntityDef> lhs;
        private Class<? extends EntityDef> rhs;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, String> getDescription() { return description; }
        public void setDescription(Map<String, String> description) { this.description = description; }
        public Class<? extends EntityDef> getLhs() { return lhs; }
        public void setLhs(Class<? extends EntityDef> lhs) { this.lhs = lhs; }
        public Class<? extends EntityDef> getRhs() { return rhs; }
        public void setRhs(Class<? extends EntityDef> rhs) { this.rhs = rhs; }
    }

    /** Human entity type. */
    public static class HumanEntity extends EntityDef {
        public HumanEntity() {
            setName("Human");
        }
    }

    /** AI assistant entity type. */
    public static class AIEntity extends EntityDef {
        public AIEntity() {
            setName("AI");
        }
    }
}
