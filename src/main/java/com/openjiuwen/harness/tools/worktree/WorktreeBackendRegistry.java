/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mirrors Python's backend registry helpers in
 * {@code openjiuwen/harness/tools/worktree/backend.py}.
 */
public final class WorktreeBackendRegistry {

    private static final ConcurrentHashMap<String, Function<WorktreeConfig, ? extends WorktreeBackend>> BACKEND_REGISTRY =
            new ConcurrentHashMap<>();

    static {
        BACKEND_REGISTRY.put("git", GitBackend::new);
    }

    private WorktreeBackendRegistry() {
    }

    public static void registerWorktreeBackend(String name, Function<WorktreeConfig, ? extends WorktreeBackend> factory) {
        BACKEND_REGISTRY.put(name, factory);
    }

    public static WorktreeBackend createBackend() {
        return createBackend("git", null);
    }

    public static WorktreeBackend createBackend(String name) {
        return createBackend(name, null);
    }

    public static WorktreeBackend createBackend(String name, WorktreeConfig config) {
        Function<WorktreeConfig, ? extends WorktreeBackend> factory = BACKEND_REGISTRY.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown worktree backend '" + name + "'. Available: "
                    + BACKEND_REGISTRY.keySet());
        }
        return factory.apply(config);
    }

    static Set<String> registeredBackendNames() {
        return Set.copyOf(BACKEND_REGISTRY.keySet());
    }

    static void unregisterWorktreeBackend(String name) {
        if (!"git".equals(name)) {
            BACKEND_REGISTRY.remove(name);
        }
    }
}
