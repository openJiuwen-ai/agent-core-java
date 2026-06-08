/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.Map;

/**
 * Mirrors Python's {@code DrawableNode} in
 * {@code openjiuwen/core/graph/visualization/drawable_node.py}.
 */
public class DrawableNode {

    private final String id;
    private String name;
    private Map<String, Object> metadata;

    public DrawableNode(String id) {
        this(id, null, null);
    }

    public DrawableNode(String id, String name, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
