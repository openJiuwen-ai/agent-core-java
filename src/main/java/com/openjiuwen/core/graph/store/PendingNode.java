/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Represents a pending (failed or interrupted) node in the graph execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.base.PendingNode}.
 * 
 * @since 0.1.7
 */
public class PendingNode implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String nodeName;
    private final String status;
    private final List<Exception> exceptions;

    /**
     * PendingNode.
     * 
     * @param nodeName nodeName
     * @param status status
     * @since 0.1.7
     */
    public PendingNode(String nodeName, String status) {
        this(nodeName, status, null);
    }

    /**
     * PendingNode.
     * 
     * @param nodeName nodeName
     * @param status status
     * @param exceptions exceptions
     * @since 0.1.7
     */
    public PendingNode(String nodeName, String status, List<Exception> exceptions) {
        this.nodeName = nodeName;
        this.status = status;
        this.exceptions = exceptions;
    }

    /**
     * getNodeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStatus() {
        return status;
    }

    /**
     * getExceptions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Exception> getExceptions() {
        return exceptions;
    }
}
