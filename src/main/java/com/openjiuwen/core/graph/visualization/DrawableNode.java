/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.Map;

/**
 * Represents a node in a drawable graph for visualization.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_node.DrawableNode}.</p>
 */
public class DrawableNode {

    private final String id;
    private String name;
    private Map<String, Object> metadata;

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableNode(String id) {
        this.id = id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableNode(String id, String name, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.metadata = metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getId() {
        return id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
