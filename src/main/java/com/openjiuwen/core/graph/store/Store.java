/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 图状态存储接口。
 * 
 * <p>定义了图状态的存储、获取和删除操作。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/base.py - Store
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Store {
    
    /**
     * 获取指定会话和命名空间的图状态。
     *
     * @param sessionId 会话标识符
     * @param ns 命名空间
     * @return 包含图状态的 CompletableFuture，如果不存在则为空的 Optional
     */
    CompletableFuture<Optional<GraphState>> get(String sessionId, String ns);
    
    /**
     * 保存图状态。
     *
     * @param sessionId 会话标识符
     * @param ns 命名空间
     * @param state 要保存的图状态
     * @return 操作完成时的 CompletableFuture
     */
    CompletableFuture<Void> save(String sessionId, String ns, GraphState state);
    
    /**
     * 删除指定会话的图状态。
     * 
     * <p>如果提供了命名空间，则删除以该命名空间为前缀的所有状态；
     * 否则删除该会话的所有状态。
     *
     * @param sessionId 会话标识符
     * @param ns 命名空间前缀，可为 null
     * @return 操作完成时的 CompletableFuture
     */
    CompletableFuture<Void> delete(String sessionId, String ns);
    
    /**
     * 删除指定会话的所有图状态。
     *
     * @param sessionId 会话标识符
     * @return 操作完成时的 CompletableFuture
     */
    default CompletableFuture<Void> delete(String sessionId) {
        return delete(sessionId, null);
    }
}

