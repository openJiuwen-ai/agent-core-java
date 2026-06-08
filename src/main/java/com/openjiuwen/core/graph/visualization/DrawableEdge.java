/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

/**
 * Mirrors Python's {@code DrawableEdge} in
 * {@code openjiuwen/core/graph/visualization/drawable_edge.py}.
 */
public class DrawableEdge {

    private final String source;
    private final String target;
    private Stringifiable data;
    private boolean conditional;
    private boolean streaming;

    public DrawableEdge(String source, String target) {
        this(source, target, null, false, false);
    }

    public DrawableEdge(String source, String target, Stringifiable data, boolean conditional, boolean streaming) {
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

    public Stringifiable getData() {
        return data;
    }

    public void setData(Stringifiable data) {
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
