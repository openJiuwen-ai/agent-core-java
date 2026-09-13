/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.Map;

/**
 * Represents a node in a drawable graph for visualization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_node.DrawableNode}.
 * </p>
 * 
 * @since 0.1.7
 */
public class DrawableNode {
    private final String id;
    private String name;
    private Map<String, Object> metadata;

    /**
     * DrawableNode.
     * 
     * @param id id
     * @since 0.1.7
     */
    public DrawableNode(String id) {
        this.id = id;
    }

    /**
     * DrawableNode.
     * 
     * @param id id
     * @param name name
     * @param metadata metadata
     * @since 0.1.7
     */
    public DrawableNode(String id, String name, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.metadata = metadata;
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
