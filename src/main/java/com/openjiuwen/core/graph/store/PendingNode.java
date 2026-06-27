/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code PendingNode} in
 * {@code openjiuwen/core/graph/store/base.py}.
 */
public class PendingNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nodeName;
    private final String status;
    private final List<Exception> exception;

    public PendingNode(String nodeName, String status) {
        this(nodeName, status, null);
    }

    public PendingNode(String nodeName, String status, List<Exception> exception) {
        this.nodeName = nodeName;
        this.status = status;
        this.exception = exception != null ? new ArrayList<>(exception) : null;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getStatus() {
        return status;
    }

    public List<Exception> getException() {
        return getExceptions();
    }

    public List<Exception> getExceptions() {
        return exception != null ? new ArrayList<>(exception) : null;
    }
}
