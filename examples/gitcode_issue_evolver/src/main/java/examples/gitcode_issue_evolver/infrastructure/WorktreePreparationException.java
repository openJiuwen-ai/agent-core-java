/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

/**
 * Reports a sanitized failure while creating an isolated Worktree.
 *
 * @since 0.1.12
 */
public final class WorktreePreparationException extends IllegalStateException {
    private final String operation;

    /**
     * Create a preparation failure while retaining the internal cause.
     *
     * @param operation failed operation name
     * @param safeDetail sanitized diagnostic summary
     * @param cause internal failure cause
     */
    public WorktreePreparationException(String operation, String safeDetail, Throwable cause) {
        super(message(operation, safeDetail), cause);
        this.operation = operation;
    }

    /**
     * Return the failed operation identifier.
     *
     * @return operation identifier
     */
    public String operation() {
        return operation;
    }

    private static String message(String operation, String safeDetail) {
        String name = operation == null || operation.isBlank() ? "worktree operation" : operation;
        String detail = safeDetail == null ? "" : safeDetail.strip();
        return detail.isBlank() ? name + " failed" : name + " failed: " + detail;
    }
}
