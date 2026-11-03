/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.Value;

import java.util.List;

/**
 * Result for atomic dependency graph mutations.
 * 
 * @since 0.1.7
 */
@Value(staticConstructor = "of")
public class GraphMutationResult {
    boolean isOk;
    String reason;
    List<TaskRecord> refreshedTasks;

    /**
     * success.
     * 
     * @param refreshedTasks refreshedTasks
     * @return the result
     * @since 0.1.7
     */
    public static GraphMutationResult success(List<TaskRecord> refreshedTasks) {
        return of(true, "", refreshedTasks != null ? refreshedTasks : List.of());
    }

    /**
     * fail.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
     */
    public static GraphMutationResult fail(String reason) {
        return of(false, reason != null ? reason : "", List.of());
    }
}
