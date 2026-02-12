// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.tracer.TracerDecorator;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Model管理器
 * 
 * 对应Python: resources_manager/model_manager.py - ModelMgr
 * 
 * @param <T> Model类型
 */
public class ModelMgr<T> extends AbstractManager<T> {
    
    public ModelMgr() {
        super();
    }
    
    /**
     * 添加Model（同步Provider）
     * 
     * @param modelId Model ID
     * @param provider 同步Provider
     */
    public void addModel(String modelId, Supplier<T> provider) {
        registerResourceProvider(modelId, provider);
    }
    
    /**
     * 添加Model（异步Provider）
     * 
     * @param modelId Model ID
     * @param provider 异步Provider
     */
    public void addAsyncModel(String modelId, Supplier<CompletableFuture<T>> provider) {
        registerAsyncResourceProvider(modelId, provider);
    }
    
    /**
     * 移除Model
     * 
     * @param modelId Model ID
     * @return 被移除的Provider，如果不存在返回null
     */
    public Supplier<?> removeModel(String modelId) {
        return unregisterResourceProvider(modelId);
    }
    
    /**
     * 获取Model（带Trace装饰）
     * 
     * @param modelId Model ID
     * @param session 会话（用于Trace装饰，可为null）
     * @return 包含TracedModel的CompletableFuture
     */
    public CompletableFuture<TracerDecorator.TracedModel<T>> getModel(String modelId, AgentSession session) {
        return getResourceAsync(modelId).thenApply(model -> {
            if (model == null) {
                return null;
            }
            // 使用适配器将 AgentSession 适配到 TracerDecorator.AgentSession
            TracerDecorator.AgentSession adaptedSession = AgentSessionAdapter.of(session);
            return TracerDecorator.decorateModelWithTrace(model, adaptedSession);
        });
    }
    
    /**
     * 获取原始Model（不带Trace装饰）
     * 
     * @param modelId Model ID
     * @return 包含原始Model的CompletableFuture
     */
    public CompletableFuture<T> getRawModel(String modelId) {
        return getResourceAsync(modelId);
    }
}

