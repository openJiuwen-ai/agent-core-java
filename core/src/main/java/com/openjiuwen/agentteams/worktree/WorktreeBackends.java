/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import com.openjiuwen.agentteams.messager.Messager;

/**
 * WorktreeBackends.
 * 
 * @since 0.1.7
 */
public final class WorktreeBackends {
    /**
     * WorktreeBackends.
     * 
     * @since 0.1.7
     */
    private WorktreeBackends() {
    }

    /**
     * createBackend.
     * 
     * @param name name
     * @param config config
     * @param messager messager
     * @param nodeId nodeId
     * @return the result
     * @since 0.1.7
     */
    public static Object createBackend(String name, WorktreeConfig config, Messager messager, String nodeId) {
        if ("remote".equals(name) || (messager != null && nodeId != null && !nodeId.isBlank())) {
            return new RemoteWorktreeBackend(config, messager, nodeId);
        }
        if (name == null || name.isBlank() || "git".equals(name)) {
            return new WorktreeManager(config);
        }
        throw new IllegalArgumentException("Unknown worktree backend '" + name + "'");
    }
}
