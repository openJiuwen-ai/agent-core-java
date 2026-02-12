/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 图状态存储的包装实现类。
 * 
 * <p>将存储操作委托给底层的 Store 实现。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/base.py - GraphStore
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class GraphStore implements Store {
    
    private final Store saver;
    
    /**
     * 构造一个 GraphStore 对象。
     *
     * @param saver 底层的存储实现
     */
    public GraphStore(Store saver) {
        this.saver = saver;
    }
    
    @Override
    public CompletableFuture<Optional<GraphState>> get(String sessionId, String ns) {
        return saver.get(sessionId, ns);
    }
    
    @Override
    public CompletableFuture<Void> save(String sessionId, String ns, GraphState state) {
        return saver.save(sessionId, ns, state);
    }
    
    @Override
    public CompletableFuture<Void> delete(String sessionId, String ns) {
        return saver.delete(sessionId, ns);
    }
}

