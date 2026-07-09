/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

/**
 * Represents an edge in a drawable graph for visualization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_edge.DrawableEdge}.
 * </p>
 * 
 * @since 0.1.7
 */
public class DrawableEdge {
    private final String source;
    private final String target;
    private Object data;
    private boolean conditional;
    private boolean streaming;

    /**
     * DrawableEdge.
     * 
     * @param source source
     * @param target target
     * @since 0.1.7
     */
    public DrawableEdge(String source, String target) {
        this.source = source;
        this.target = target;
    }

    /**
     * DrawableEdge.
     * 
     * @param source source
     * @param target target
     * @param data data
     * @param conditional conditional
     * @param streaming streaming
     * @since 0.1.7
     */
    public DrawableEdge(String source, String target, Object data, boolean conditional, boolean streaming) {
        this.source = source;
        this.target = target;
        this.data = data;
        this.conditional = conditional;
        this.streaming = streaming;
    }

    /**
     * getSource.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSource() {
        return source;
    }

    /**
     * getTarget.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTarget() {
        return target;
    }

    /**
     * getData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getData() {
        return data;
    }

    /**
     * setData.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * isConditional.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isConditional() {
        return conditional;
    }

    /**
     * setConditional.
     * 
     * @param conditional conditional
     * @since 0.1.7
     */
    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }

    /**
     * isStreaming.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * setStreaming.
     * 
     * @param streaming streaming
     * @since 0.1.7
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
}
