/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-size stripe lock table keyed by string hash.
 * <p>
 * Concurrent operations on different keys typically take different stripes and proceed in parallel,
 * while the same key always maps to the same {@link ReentrantLock}.
 */
public final class StripedKeyLocks {
    private final ReentrantLock[] stripes;
    private final int mask;

    /**
     * Creates a stripe table with at least {@code stripeCount} locks (rounded up to a power of two).
     *
     * @param stripeCount requested minimum number of stripes; values below 1 are treated as 1
     */
    public StripedKeyLocks(int stripeCount) {
        int size = 1;
        int requested = Math.max(1, stripeCount);
        while (size < requested) {
            size <<= 1;
        }
        this.stripes = new ReentrantLock[size];
        for (int i = 0; i < size; i++) {
            this.stripes[i] = new ReentrantLock();
        }
        this.mask = size - 1;
    }

    /**
     * Returns the stripe lock for the given key.
     *
     * @param key partition key; {@code null} maps to stripe 0
     * @return the non-null {@link ReentrantLock} for this key
     */
    public ReentrantLock get(String key) {
        int hash = key == null ? 0 : key.hashCode();
        return stripes[hash & mask];
    }
}
