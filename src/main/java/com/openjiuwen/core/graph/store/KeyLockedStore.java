/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.common.concurrent.StripedKeyLocks;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link Store} decorator that serializes get/save/delete per {@code sessionId} via striped locks.
 * <p>
 * Different sessions may proceed concurrently; operations on the same session are mutually exclusive.
 *
 * @since 0.1.14
 */
public class KeyLockedStore implements Store {
    private static final int DEFAULT_STRIPES = 64;

    private final Store delegate;
    private final StripedKeyLocks locks;

    /**
     * Wraps {@code delegate} with the default stripe count.
     *
     * @param delegate underlying store that performs the actual persistence
     * @since 0.1.14
     */
    public KeyLockedStore(Store delegate) {
        this(delegate, new StripedKeyLocks(DEFAULT_STRIPES));
    }

    /**
     * Wraps {@code delegate} with a custom stripe lock table.
     *
     * @param delegate underlying store that performs the actual persistence
     * @param locks stripe locks; {@code null} falls back to the default stripe count
     * @since 0.1.14
     */
    public KeyLockedStore(Store delegate, StripedKeyLocks locks) {
        this.delegate = delegate;
        this.locks = locks != null ? locks : new StripedKeyLocks(DEFAULT_STRIPES);
    }

    /**
     * Returns the wrapped store (useful for tests that assert the decorator chain).
     *
     * @return the non-null delegate store
     * @since 0.1.14
     */
    public Store delegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.14
     */
    @Override
    public Optional<GraphStoreState> get(String sessionId, String ns) {
        ReentrantLock lock = locks.get(sessionId);
        lock.lock();
        try {
            return delegate.get(sessionId, ns);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.14
     */
    @Override
    public void save(String sessionId, String ns, GraphStoreState state) {
        ReentrantLock lock = locks.get(sessionId);
        lock.lock();
        try {
            delegate.save(sessionId, ns, state);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.14
     */
    @Override
    public void delete(String sessionId, String ns) {
        ReentrantLock lock = locks.get(sessionId);
        lock.lock();
        try {
            delegate.delete(sessionId, ns);
        } finally {
            lock.unlock();
        }
    }
}
