/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.util.Objects;

/** Bounded structured failure emitted across the Issue worker boundary. */
public record IssueFailure(String code, IssueFailureCategory category,
                           String summary, String diagnostic,
                           boolean safeToReplay) {
    private static final int MAX_SUMMARY = 500;
    private static final int MAX_DIAGNOSTIC = 4_000;

    /** Validate and bound failure fields. */
    public IssueFailure {
        code = required(code, "code", 100);
        category = Objects.requireNonNull(category, "category must not be null");
        summary = required(summary, "summary", MAX_SUMMARY);
        diagnostic = limit(diagnostic == null ? "" : diagnostic, MAX_DIAGNOSTIC);
    }

    private static String required(String value, String name, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return limit(value.strip(), maximum);
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
