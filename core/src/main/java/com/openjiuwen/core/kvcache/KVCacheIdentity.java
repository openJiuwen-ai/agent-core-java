/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.Objects;

/**
 * Provider-facing KV cache lineage identity.
 *
 * <p>Mirrors Python's {@code KVCacheIdentity} in
 * {@code openjiuwen/core/kv_cache/kv_cache_types.py}. {@code cacheId} is the
 * session's own cache key and {@code parentCacheId} points at its parent
 * (the product session root). The gateway organizes caches as a
 * (root, child) tree: evicting a root cascades to the whole subtree.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheIdentity {
    private final String cacheId;
    private final String parentCacheId;

    public KVCacheIdentity(String cacheId, String parentCacheId) {
        this.cacheId = cacheId == null ? "" : cacheId;
        this.parentCacheId = parentCacheId == null ? "" : parentCacheId;
    }

    public String cacheId() {
        return cacheId;
    }

    public String parentCacheId() {
        return parentCacheId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KVCacheIdentity identity)) {
            return false;
        }
        return cacheId.equals(identity.cacheId) && parentCacheId.equals(identity.parentCacheId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheId, parentCacheId);
    }

    @Override
    public String toString() {
        return "KVCacheIdentity[" + cacheId + ", " + parentCacheId + "]";
    }
}
