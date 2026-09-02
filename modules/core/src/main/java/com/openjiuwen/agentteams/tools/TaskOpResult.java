/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import lombok.Value;

/**
 * Outcome of a task mutation with the Python TaskOpResult-style failure reason.
 * 
 * @since 0.1.7
 */
@Value(staticConstructor = "of")
public class TaskOpResult {
    boolean isOk;
    String reason;

    /**
     * success.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static TaskOpResult success() {
        return of(true, "");
    }

    /**
     * fail.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
     */
    public static TaskOpResult fail(String reason) {
        return of(false, reason != null ? reason : "");
    }
}
