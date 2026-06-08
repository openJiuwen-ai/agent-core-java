/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Outcome of mutating the task dependency graph.
 *
 * <p>Mirrors Python's {@code GraphMutationResult} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
public record GraphMutationResult(
        boolean ok,
        String reason,
        @JsonProperty("refreshed_tasks") List<Object> refreshedTasks
) {

    public GraphMutationResult {
        reason = reason == null ? "" : reason;
        refreshedTasks = refreshedTasks == null ? List.of() : List.copyOf(refreshedTasks);
    }

    public static GraphMutationResult success(List<Object> refreshedTasks) {
        return new GraphMutationResult(true, "", refreshedTasks);
    }

    public static GraphMutationResult fail(String reason) {
        return new GraphMutationResult(false, reason, List.of());
    }
}
