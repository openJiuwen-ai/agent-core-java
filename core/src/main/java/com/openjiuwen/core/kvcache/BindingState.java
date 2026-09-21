/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.Objects;

/**
 * One registered session binding plus its cache state.
 *
 * <p>Mirrors Python's {@code BindingState} and {@code KVCacheBinding} in
 * {@code openjiuwen/core/kv_cache/kv_cache_types.py}.</p>
 *
 * @since 0.1.16
 */
public final class BindingState {
    private final Object model;
    private final KVCacheIdentity identity;
    private final KVCacheControlDomain controlDomain;
    private final boolean isFallback;
    private Residency residency = Residency.UNKNOWN;
    private int revision;

    public BindingState(KVCacheIdentity identity, Object model, KVCacheControlDomain controlDomain,
                        boolean isFallback) {
        this.identity = identity;
        this.model = model;
        this.controlDomain = controlDomain;
        this.isFallback = isFallback;
    }

    public KVCacheIdentity identity() {
        return identity;
    }

    public Object model() {
        return model;
    }

    public KVCacheControlDomain controlDomain() {
        return controlDomain;
    }

    public boolean isFallback() {
        return isFallback;
    }

    public Residency residency() {
        return residency;
    }

    public void setResidency(Residency residency) {
        this.residency = residency;
    }

    public int revision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    @Override
    public String toString() {
        return "BindingState[" + identity + ", " + controlDomain + ", " + residency
                + ", revision=" + revision + ", fallback=" + isFallback + "]";
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, controlDomain);
    }

    /** Physical location of a cache. */
    public enum Residency {
        /** Cache lives in inference-side memory. */
        RESIDENT,
        /** Cache moved to external storage. */
        OFFLOADED,
        /** Cache location unknown, including right after a failure. */
        UNKNOWN
    }
}
