/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

/**
 * Outcome of a task mutation with a human-readable failure reason.
 *
 * <p>Mirrors Python's {@code TaskOpResult} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
public record TaskOpResult(boolean ok, String reason) {

    public TaskOpResult {
        reason = reason == null ? "" : reason;
    }

    public static TaskOpResult success() {
        return new TaskOpResult(true, "");
    }

    public static TaskOpResult fail(String reason) {
        return new TaskOpResult(false, reason);
    }
}
