/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code WorktreeLifecyclePolicy} in
 * {@code openjiuwen/harness/tools/worktree/models.py}.
 */
public enum WorktreeLifecyclePolicy {
    AUTO("auto"),
    EPHEMERAL("ephemeral"),
    DURABLE("durable");

    private final String value;

    WorktreeLifecyclePolicy(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WorktreeLifecyclePolicy fromValue(String value) {
        for (WorktreeLifecyclePolicy policy : values()) {
            if (policy.value.equals(value)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("Unknown worktree lifecycle policy: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
