/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.Value;

import java.util.List;

/**
 * Result for atomic dependency graph mutations.
 */
@Value(staticConstructor = "of")
public class GraphMutationResult {
    boolean isOk;
    String reason;
    List<TaskRecord> refreshedTasks;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static GraphMutationResult success(List<TaskRecord> refreshedTasks) {
        return of(true, "", refreshedTasks != null ? refreshedTasks : List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static GraphMutationResult fail(String reason) {
        return of(false, reason != null ? reason : "", List.of());
    }
}
