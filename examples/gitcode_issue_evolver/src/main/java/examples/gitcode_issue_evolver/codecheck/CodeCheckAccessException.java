/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

/**
 * Typed failure from the controlled CodeCheck report boundary.
 *
 * @since 0.1.12
 */
public final class CodeCheckAccessException extends RuntimeException {
    private final boolean retryable;

    public CodeCheckAccessException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public CodeCheckAccessException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** @return whether a later polling cycle may safely retry */
    public boolean isRetryable() {
        return retryable;
    }
}
