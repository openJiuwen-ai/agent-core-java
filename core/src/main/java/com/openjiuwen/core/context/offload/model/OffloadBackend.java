/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

/**
 * Physical storage backends for offloaded payloads.
 *
 * <p>{@link #COMPOSITE} is only used as the aggregate descriptor of a
 * fallback chain; individual entries always commit to a concrete backend
 * so loads can be directed via {@code CompositeOffloadStore#storeOf}.</p>
 *
 * @since 2026-01-01
 */
public enum OffloadBackend {
    /** In-process ConcurrentHashMap store, lost on JVM exit. */
    MEMORY,
    /** Atomic-write JSON files under the session workspace. */
    FILESYSTEM,
    /** Redis via Jedis, {@code SET key value EX ttl}. */
    REDIS,
    /** Aggregate of a 1..N fallback chain; never a commit target. */
    COMPOSITE
}
