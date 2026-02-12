/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 表示图执行过程中待处理节点的状态信息。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/base.py - PendingNode
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class PendingNode {
    
    private final String nodeName;
    private final String status;
    private final List<Exception> exception;
    
    /**
     * 构造一个 PendingNode 对象。
     *
     * @param nodeName 节点名称
     * @param status 节点状态
     * @param exception 异常列表，可为 null
     */
    public PendingNode(String nodeName, String status, List<Exception> exception) {
        this.nodeName = nodeName;
        this.status = status;
        this.exception = exception != null ? new ArrayList<>(exception) : null;
    }
    
    /**
     * 构造一个没有异常的 PendingNode 对象。
     *
     * @param nodeName 节点名称
     * @param status 节点状态
     */
    public PendingNode(String nodeName, String status) {
        this(nodeName, status, null);
    }
    
    /**
     * 获取节点名称。
     *
     * @return 节点名称
     */
    public String getNodeName() {
        return nodeName;
    }
    
    /**
     * 获取节点状态。
     *
     * @return 节点状态
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * 获取异常列表。
     *
     * @return 异常列表，可能为 null
     */
    public List<Exception> getException() {
        return exception != null ? new ArrayList<>(exception) : null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PendingNode that = (PendingNode) o;
        return Objects.equals(nodeName, that.nodeName) &&
               Objects.equals(status, that.status) &&
               Objects.equals(exception, that.exception);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeName, status, exception);
    }
    
    @Override
    public String toString() {
        return "PendingNode{" +
               "nodeName='" + nodeName + '\'' +
               ", status='" + status + '\'' +
               ", exception=" + exception +
               '}';
    }
}

