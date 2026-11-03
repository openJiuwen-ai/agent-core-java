/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.common.concurrent.StripedKeyLocks;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link Store} decorator that serializes get/save/delete per {@code sessionId} via striped locks.
 * <p>
 * Different sessions may proceed concurrently; operations on the same session are mutually exclusive
 * for the duration of the underlying async stage.
 */
public class KeyLockedStore implements Store {
    private static final int DEFAULT_STRIPES = 64;

    private final Store delegate;
    private final StripedKeyLocks locks;

    public KeyLockedStore(Store delegate) {
        this(delegate, new StripedKeyLocks(DEFAULT_STRIPES));
    }

    public KeyLockedStore(Store delegate, StripedKeyLocks locks) {
        this.delegate = delegate;
        this.locks = locks != null ? locks : new StripedKeyLocks(DEFAULT_STRIPES);
    }

    public Store delegate() {
        return delegate;
    }

    @Override
    public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
        return locked(sessionId, () -> delegate.get(sessionId, ns));
    }

    @Override
    public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
        return locked(sessionId, () -> delegate.save(sessionId, ns, state));
    }

    @Override
    public CompletionStage<Void> delete(String sessionId, String ns) {
        return locked(sessionId, () -> delegate.delete(sessionId, ns));
    }

    private <T> CompletionStage<T> locked(String sessionId, java.util.function.Supplier<CompletionStage<T>> action) {
        ReentrantLock lock = locks.get(sessionId);
        lock.lock();
        try {
            return action.get().whenComplete((ignored, error) -> lock.unlock());
        } catch (RuntimeException | Error error) {
            lock.unlock();
            throw error;
        }
    }
}
