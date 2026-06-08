/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mirrors Python's {@code PendingCommitResult} in
 * {@code openjiuwen/agent_evolving/experience/lifecycle.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class PendingCommitResult {

    private final int appliedCount;
    private final int pendingCount;
    private final int rejectedCount;
    private final List<String> errors;

    public PendingCommitResult(int appliedCount, int pendingCount) {
        this(appliedCount, pendingCount, 0, List.of());
    }

    public PendingCommitResult(int appliedCount, int pendingCount, int rejectedCount, List<String> errors) {
        this.appliedCount = appliedCount;
        this.pendingCount = pendingCount;
        this.rejectedCount = rejectedCount;
        this.errors = immutableList(errors);
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
