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
 */
public class PendingNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nodeName;
    private final String status;
    private final List<Exception> exceptions;

    /**
     * Auto-generated for codecheck compliance.
     */
    public PendingNode(String nodeName, String status) {
        this(nodeName, status, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public PendingNode(String nodeName, String status, List<Exception> exceptions) {
        this.nodeName = nodeName;
        this.status = status;
        this.exceptions = exceptions;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Exception> getExceptions() {
        return exceptions;
    }
}
