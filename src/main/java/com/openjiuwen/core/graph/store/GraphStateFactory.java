/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GraphState 的工厂类。
 * 
 * <p>提供创建 GraphState 的静态工厂方法。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/base.py - create_state
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class GraphStateFactory {
    
    private GraphStateFactory() {
        // 工具类，禁止实例化
    }
    
    /**
     * 创建一个新的 GraphState 对象。
     *
     * @param ns 命名空间
     * @param step 执行步骤
     * @param channelSnapshot 通道快照
     * @param pendingBuffer 待处理消息缓冲区，可为 null
     * @param pendingNode 待处理节点映射，可为 null
     * @param nodeVersion 节点版本映射，可为 null
     * @return 新创建的 GraphState 对象
     */
    public static GraphState createState(String ns, int step, Map<String, Object> channelSnapshot,
                                         List<Message> pendingBuffer, Map<String, PendingNode> pendingNode,
                                         Map<String, Integer> nodeVersion) {
        return new GraphState(
            ns,
            step,
            channelSnapshot,
            pendingBuffer != null ? pendingBuffer : Collections.emptyList(),
            pendingNode != null ? pendingNode : Collections.emptyMap(),
            nodeVersion != null ? nodeVersion : Collections.emptyMap()
        );
    }
    
    /**
     * 创建一个带有默认值的 GraphState 对象。
     *
     * @param ns 命名空间
     * @param step 执行步骤
     * @param channelSnapshot 通道快照
     * @return 新创建的 GraphState 对象
     */
    public static GraphState createState(String ns, int step, Map<String, Object> channelSnapshot) {
        return createState(ns, step, channelSnapshot, null, null, null);
    }
}

