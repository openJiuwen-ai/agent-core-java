/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

import java.util.List;

/**
 * Result of an atomic task dependency graph mutation.
 *
 * <p>Mirrors Python's {@code GraphMutationResult} in
 * {@code openjiuwen.agent_teams.schema.task}.</p>
 */
public record GraphMutationResult(boolean ok, String reason, List<Object> refreshedTasks) {

    public GraphMutationResult {
        reason = reason != null ? reason : "";
        refreshedTasks = refreshedTasks != null ? List.copyOf(refreshedTasks) : List.of();
    }

    public static GraphMutationResult success() {
        return new GraphMutationResult(true, "", List.of());
    }

    public static GraphMutationResult success(List<?> refreshedTasks) {
        return new GraphMutationResult(true, "", List.copyOf(refreshedTasks));
    }

    public static GraphMutationResult fail(String reason) {
        return new GraphMutationResult(false, reason, List.of());
    }
}
