/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表示图的执行状态。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/base.py - GraphState
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class GraphState {
    
    private final String ns;
    private final int step;
    private final Map<String, Object> channelValues;
    private final List<Message> pendingBuffer;
    private final Map<String, PendingNode> pendingNode;
    private final Map<String, Integer> nodeVersion;
    
    /**
     * 构造一个 GraphState 对象。
     *
     * @param ns 命名空间
     * @param step 执行步骤
     * @param channelValues 通道值映射
     * @param pendingBuffer 待处理消息缓冲区
     * @param pendingNode 待处理节点映射
     * @param nodeVersion 节点版本映射
     */
    public GraphState(String ns, int step, Map<String, Object> channelValues,
                      List<Message> pendingBuffer, Map<String, PendingNode> pendingNode,
                      Map<String, Integer> nodeVersion) {
        this.ns = ns;
        this.step = step;
        this.channelValues = channelValues != null ? new HashMap<>(channelValues) : new HashMap<>();
        this.pendingBuffer = pendingBuffer != null ? new ArrayList<>(pendingBuffer) : new ArrayList<>();
        this.pendingNode = pendingNode != null ? new HashMap<>(pendingNode) : new HashMap<>();
        this.nodeVersion = nodeVersion != null ? new HashMap<>(nodeVersion) : new HashMap<>();
    }
    
    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    public String getNs() {
        return ns;
    }
    
    /**
     * 获取执行步骤。
     *
     * @return 执行步骤
     */
    public int getStep() {
        return step;
    }
    
    /**
     * 获取通道值映射。
     *
     * @return 通道值映射的副本
     */
    public Map<String, Object> getChannelValues() {
        return new HashMap<>(channelValues);
    }
    
    /**
     * 获取待处理消息缓冲区。
     *
     * @return 待处理消息缓冲区的副本
     */
    public List<Message> getPendingBuffer() {
        return new ArrayList<>(pendingBuffer);
    }
    
    /**
     * 获取待处理节点映射。
     *
     * @return 待处理节点映射的副本
     */
    public Map<String, PendingNode> getPendingNode() {
        return new HashMap<>(pendingNode);
    }
    
    /**
     * 获取节点版本映射。
     *
     * @return 节点版本映射的副本
     */
    public Map<String, Integer> getNodeVersion() {
        return new HashMap<>(nodeVersion);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphState that = (GraphState) o;
        return step == that.step &&
               Objects.equals(ns, that.ns) &&
               Objects.equals(channelValues, that.channelValues) &&
               Objects.equals(pendingBuffer, that.pendingBuffer) &&
               Objects.equals(pendingNode, that.pendingNode) &&
               Objects.equals(nodeVersion, that.nodeVersion);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ns, step, channelValues, pendingBuffer, pendingNode, nodeVersion);
    }
    
    @Override
    public String toString() {
        return "GraphState{" +
               "ns='" + ns + '\'' +
               ", step=" + step +
               ", channelValues=" + channelValues +
               ", pendingBuffer=" + pendingBuffer +
               ", pendingNode=" + pendingNode +
               ", nodeVersion=" + nodeVersion +
               '}';
    }
}

