/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.cleanup;

import com.openjiuwen.core.context.offload.model.OffloadNamespace;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-session striped lock serializing offload save/delete within one
 * session (架构文档 §3.1, AC-2-09). Handwritten ~20-line alternative to
 * guava {@code Striped}; keys are sanitized session ids.
 *
 * <p>Usage: {@code try (SessionOffloadGate.AutoCloseableLock ignored = gate.lock(ns)) { ... }}</p>
 *
 * @since 2026-01-01
 */
public class SessionOffloadGate {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Acquires the session lock; always pair with try/finally or
     * try-with-resources on the returned handle.
     *
     * @param ns the offload namespace used to derive the session-scoped lock key
     * @return an {@link AutoCloseableLock} that releases the lock when closed
     */
    public AutoCloseableLock lock(OffloadNamespace ns) {
        Objects.requireNonNull(ns, "ns must not be null");
        ReentrantLock lock = locks.computeIfAbsent(lockKey(ns), key -> new ReentrantLock());
        lock.lock();
        return lock::unlock;
    }

    /**
     * Number of live session locks (diagnostics/tests).
     *
     * @return the count of currently tracked session locks
     */
    public int lockCount() {
        return locks.size();
    }

    private static String lockKey(OffloadNamespace ns) {
        return ns.sanitized().sessionId();
    }

    /**
     * Named handle so callers can declare the resource in try-with-resources.
     */
    public interface AutoCloseableLock extends AutoCloseable {
        @Override
        void close();
    }
}
