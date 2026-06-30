/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import lombok.Value;

/**
 * Outcome of a task mutation with the Python TaskOpResult-style failure reason.
 */
@Value(staticConstructor = "of")
public class TaskOpResult {
    boolean isOk;
    String reason;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskOpResult success() {
        return of(true, "");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskOpResult fail(String reason) {
        return of(false, reason != null ? reason : "");
    }
}
