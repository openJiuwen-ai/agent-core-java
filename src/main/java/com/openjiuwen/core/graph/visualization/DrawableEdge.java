/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

/**
 * Represents an edge in a drawable graph for visualization.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_edge.DrawableEdge}.</p>
 */
public class DrawableEdge {

    private final String source;
    private final String target;
    private Object data;
    private boolean conditional;
    private boolean streaming;

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableEdge(String source, String target) {
        this.source = source;
        this.target = target;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DrawableEdge(String source, String target, Object data, boolean conditional, boolean streaming) {
        this.source = source;
        this.target = target;
        this.data = data;
        this.conditional = conditional;
        this.streaming = streaming;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSource() {
        return source;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getData() {
        return data;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isConditional() {
        return conditional;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
}
