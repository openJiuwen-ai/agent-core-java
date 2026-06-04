/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree.models;

import com.openjiuwen.agent_teams.worktree.WorktreeConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Worktree backend registry.
 *
 * <p>Mirrors Python's {@code create_backend} and {@code register_worktree_backend}
 * in {@code openjiuwen.agent_teams.worktree.backend}.</p>
 */
public final class WorktreeBackends {

    private static final Map<String, Function<WorktreeConfig, WorktreeBackend>> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("git", GitBackend::new);
    }

    private WorktreeBackends() {
    }

    public static synchronized void registerWorktreeBackend(
            String name,
            Function<WorktreeConfig, WorktreeBackend> factory
    ) {
        REGISTRY.put(name, factory);
    }

    public static synchronized void unregisterWorktreeBackend(String name) {
        if (!"git".equals(name)) {
            REGISTRY.remove(name);
        }
    }

    public static WorktreeBackend createBackend(String name) {
        return createBackend(name, null);
    }

    public static synchronized WorktreeBackend createBackend(String name, WorktreeConfig config) {
        String backendName = name == null || name.isBlank() ? "git" : name;
        Function<WorktreeConfig, WorktreeBackend> factory = REGISTRY.get(backendName);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown worktree backend '" + backendName + "'. Available: " + REGISTRY.keySet());
        }
        return factory.apply(config != null ? config : new WorktreeConfig());
    }

    public static synchronized Map<String, Function<WorktreeConfig, WorktreeBackend>> registrySnapshot() {
        return Map.copyOf(REGISTRY);
    }
}
