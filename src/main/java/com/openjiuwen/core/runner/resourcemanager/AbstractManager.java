/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Generic resource-provider registry.
 *
 * <p>Mirrors Python's {@code AbstractManager} in
 * {@code openjiuwen/core/runner/resources_manager/abstract_manager.py}.</p>
 *
 * @param <T> resource type
 */
public class AbstractManager<T> {

    private final ThreadSafeDict<String, Supplier<?>> providers = new ThreadSafeDict<>();

    protected void registerResourceProvider(String resourceId, Supplier<?> resource) {
        if (providers.get(resourceId) != null) {
            throw new IllegalArgumentException("add resource failed, " + resourceId + " is already exist");
        }
        providers.put(resourceId, resource);
    }

    @SuppressWarnings("unchecked")
    protected CompletionStage<T> getResource(String resourceId) {
        Supplier<?> provider = providers.get(resourceId);
        if (provider == null) {
            return CompletableFuture.completedFuture(null);
        }
        Object value = provider.get();
        if (value instanceof CompletionStage<?> stage) {
            return stage.thenApply(item -> (T) item);
        }
        return CompletableFuture.completedFuture((T) value);
    }

    @SuppressWarnings("unchecked")
    protected Supplier<?> unregisterResourceProvider(String resourceId) {
        return providers.pop(resourceId, null);
    }
}
