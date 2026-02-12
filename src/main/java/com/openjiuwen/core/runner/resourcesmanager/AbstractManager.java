// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 资源管理器抽象基类
 * 
 * 对应Python: resources_manager/abstract_manager.py - AbstractManager
 * 
 * @param <T> 管理的资源类型
 */
public abstract class AbstractManager<T> {
    
    protected final ThreadSafeDict<String, Supplier<?>> providers = new ThreadSafeDict<>();
    
    /**
     * 注册同步资源提供者
     * 
     * @param resourceId 资源ID
     * @param provider 同步Provider
     * @throws IllegalArgumentException 如果资源ID已存在
     */
    protected void registerResourceProvider(String resourceId, Supplier<T> provider) {
        if (providers.containsKey(resourceId)) {
            throw new IllegalArgumentException("add resource failed, " + resourceId + " is already exist");
        }
        providers.put(resourceId, provider);
    }
    
    /**
     * 注册异步资源提供者
     * 
     * @param resourceId 资源ID
     * @param provider 异步Provider（返回CompletableFuture）
     * @throws IllegalArgumentException 如果资源ID已存在
     */
    protected void registerAsyncResourceProvider(String resourceId, Supplier<CompletableFuture<T>> provider) {
        if (providers.containsKey(resourceId)) {
            throw new IllegalArgumentException("add resource failed, " + resourceId + " is already exist");
        }
        providers.put(resourceId, provider);
    }
    
    /**
     * 异步获取资源
     * 
     * @param resourceId 资源ID
     * @return 包含资源的CompletableFuture，如果不存在返回包含null的CompletableFuture
     */
    @SuppressWarnings("unchecked")
    protected CompletableFuture<T> getResourceAsync(String resourceId) {
        Supplier<?> provider = providers.get(resourceId);
        if (provider == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            Object result = provider.get();
            if (result instanceof CompletableFuture) {
                return (CompletableFuture<T>) result;
            } else {
                return CompletableFuture.completedFuture((T) result);
            }
        } catch (Exception e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * 注销资源提供者
     * 
     * @param resourceId 资源ID
     * @return 被移除的Provider，如果不存在返回null
     */
    protected Supplier<?> unregisterResourceProvider(String resourceId) {
        return providers.pop(resourceId, null);
    }
    
    /**
     * 检查是否包含指定资源的Provider
     * 
     * @param resourceId 资源ID
     * @return 如果存在返回true
     */
    protected boolean containsProvider(String resourceId) {
        return providers.containsKey(resourceId);
    }
    
    /**
     * 获取Provider数量
     * 
     * @return Provider数量
     */
    protected int getProviderCount() {
        return providers.size();
    }
}

