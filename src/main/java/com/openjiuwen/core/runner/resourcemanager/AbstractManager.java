  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic base class for resource managers that use provider-based registration.
 * <p>
 * Mirrors Python's {@code AbstractManager} in {@code resources_manager/abstract_manager.py}.
 *
 * @param <T> the type of resource this manager handles
 */
public abstract class AbstractManager<T> {

    protected final ConcurrentHashMap<String, Supplier<? extends T>> providers = new ConcurrentHashMap<>();

    protected void registerResourceProvider(String resourceId, Supplier<? extends T> resource) {
        if (providers.containsKey(resourceId)) {
            throw new IllegalArgumentException("add resource failed, " + resourceId + " is already exist");
        }
        providers.put(resourceId, resource);
    }

    protected T getResource(String resourceId) {
        Supplier<? extends T> provider = providers.get(resourceId);
        if (provider == null) {
            return null;
        }
        return provider.get();
    }

    protected Supplier<? extends T> unregisterResourceProvider(String resourceId) {
        return providers.remove(resourceId);
    }

    /**
     * Clear all registered providers.
     */
    protected void clearProviders() {
        providers.clear();
    }
}
