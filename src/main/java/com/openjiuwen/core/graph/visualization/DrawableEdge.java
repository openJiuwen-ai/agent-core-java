/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    public DrawableEdge(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public DrawableEdge(String source, String target, Object data, boolean conditional, boolean streaming) {
        this.source = source;
        this.target = target;
        this.data = data;
        this.conditional = conditional;
        this.streaming = streaming;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean isConditional() {
        return conditional;
    }

    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
}
