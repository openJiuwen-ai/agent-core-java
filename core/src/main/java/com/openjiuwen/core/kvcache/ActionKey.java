/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.Objects;

/**
 * Keys and scopes used to queue KV cache management actions.
 *
 * <p>Mirrors Python's {@code BindingKey}/{@code RootKey}/{@code ActionKey}
 * in {@code openjiuwen/core/kv_cache/kv_cache_types.py}.</p>
 *
 * @since 0.1.16
 */
public final class ActionKey {
    private final ActionScope scope;
    private final String cacheId;
    private final KVCacheControlDomain controlDomain;

    public ActionKey(ActionScope scope, String cacheId, KVCacheControlDomain controlDomain) {
        this.scope = scope;
        this.cacheId = cacheId;
        this.controlDomain = controlDomain;
    }

    /**
     * Build the key for one session binding.
     *
     * @param cacheId binding cache id
     * @param domain control domain of the cache
     * @return binding-scope action key
     */
    public static ActionKey bindingKey(String cacheId, KVCacheControlDomain domain) {
        return new ActionKey(ActionScope.BINDING, cacheId, domain);
    }

    /**
     * Build the key for one product-session root.
     *
     * @param parentCacheId root cache id
     * @param domain control domain of the cache
     * @return root-scope action key
     */
    public static ActionKey rootKey(String parentCacheId, KVCacheControlDomain domain) {
        return new ActionKey(ActionScope.ROOT, parentCacheId, domain);
    }

    public ActionScope scope() {
        return scope;
    }

    public String cacheId() {
        return cacheId;
    }

    public KVCacheControlDomain controlDomain() {
        return controlDomain;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActionKey key)) {
            return false;
        }
        return scope == key.scope && cacheId.equals(key.cacheId) && controlDomain.equals(key.controlDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, cacheId, controlDomain);
    }

    @Override
    public String toString() {
        return "ActionKey[" + scope + ", " + cacheId + "]";
    }

    /** Scope of one management action. */
    public enum ActionScope {
        /** Action on one session binding. */
        BINDING,
        /** Action on one product session root. */
        ROOT
    }
}
