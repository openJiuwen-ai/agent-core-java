/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.Objects;

/**
 * Control domain of one KV cache: the same cache is a different object for a
 * different (provider, api base, model, namespace) tuple.
 *
 * <p>Mirrors Python's {@code KVCacheControlDomain} in
 * {@code openjiuwen/core/kv_cache/kv_cache_types.py}.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheControlDomain {
    private final String provider;
    private final String apiBase;
    private final String modelName;
    private final String cacheNamespace;

    public KVCacheControlDomain(String provider, String apiBase, String modelName, String cacheNamespace) {
        this.provider = provider == null ? "" : provider;
        this.apiBase = stripTrailingSlash(apiBase);
        this.modelName = modelName == null ? "" : modelName;
        this.cacheNamespace = cacheNamespace == null ? "" : cacheNamespace;
    }

    private static String stripTrailingSlash(String apiBase) {
        if (apiBase == null) {
            return "";
        }
        String normalized = apiBase.strip();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String provider() {
        return provider;
    }

    public String apiBase() {
        return apiBase;
    }

    public String modelName() {
        return modelName;
    }

    public String cacheNamespace() {
        return cacheNamespace;
    }

    public String modelNameOrEmpty() {
        return modelName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KVCacheControlDomain domain)) {
            return false;
        }
        return provider.equals(domain.provider)
                && apiBase.equals(domain.apiBase)
                && modelName.equals(domain.modelName)
                && cacheNamespace.equals(domain.cacheNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, apiBase, modelName, cacheNamespace);
    }

    @Override
    public String toString() {
        return "KVCacheControlDomain[" + provider + ", " + apiBase + ", " + modelName
                + ", " + cacheNamespace + "]";
    }
}
