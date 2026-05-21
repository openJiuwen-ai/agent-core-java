/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity graph object representing a named entity in the knowledge graph.
 * <p>
 * Mirrors Python's {@code Entity} model from
 * <code>foundation/store/graph/graph_object.py</code>.
 */
public class Entity extends NamedGraphObject {

    private String entityType;
    private List<String> factIds;
    private Map<String, Object> attributes;

    public Entity() {
        super();
        this.entityType = "";
        this.attributes = new HashMap<>();
    }

    public Entity(String name, String entityType) {
        super(name);
        this.entityType = entityType;
        this.attributes = new HashMap<>();
    }

    /**
     * Create entity with name, entityType, and initial content.
     * Used in tests for update_entity tests.
     */
    public Entity(String name, String entityType, String content) {
        super(name);
        this.entityType = entityType;
        this.attributes = new HashMap<>();
        setContent(content);
    }

    /**
     * Create entity with name, entityType, content, and attributes.
     */
    public Entity(String name, String entityType, String content, Map<String, Object> attributes) {
        super(name);
        this.entityType = entityType;
        this.attributes = attributes != null ? attributes : new HashMap<>();
        setContent(content);
    }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public List<String> getFactIds() { return factIds; }
    public void setFactIds(List<String> factIds) { this.factIds = factIds; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    @Override
    public String getObjType() { return "entity"; }
}
