/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.io.Serial;
import java.util.Objects;

/** Carries a classified Issue failure through the asynchronous worker boundary. */
public final class IssueExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final IssueFailure failure;

    /** Create a classified execution exception. */
    public IssueExecutionException(IssueFailure failure, Throwable cause) {
        super(Objects.requireNonNull(failure, "failure must not be null").summary(), cause);
        this.failure = failure;
    }

    /** Return the Controller-authoritative failure. */
    public IssueFailure failure() {
        return failure;
    }
}
