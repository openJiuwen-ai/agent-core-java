/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import java.util.Objects;

/**
 * Stable internal failure vocabulary for model-facing Feature Evolver tools.
 *
 * @since 0.1.13
 */
final class FeatureToolException extends RuntimeException {
    private final Code code;

    FeatureToolException(Code code, String message) {
        super(format(code, message));
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    FeatureToolException(Code code, String message, Throwable cause) {
        super(format(code, message), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    Code code() {
        return code;
    }

    private static String format(Code code, String message) {
        Code required = Objects.requireNonNull(code, "code must not be null");
        String detail = Objects.requireNonNull(message, "message must not be null");
        return "[" + required + "] " + detail;
    }

    enum Code {
        INVALID_ARGUMENT,
        SENSITIVE_PATH,
        PATH_NOT_FOUND,
        PATH_OUTSIDE_WORKTREE,
        PATH_TYPE_UNSUPPORTED,
        FILE_TOO_LARGE,
        FILE_NOT_UTF8,
        FILE_UNREADABLE,
        OFFSET_OUT_OF_RANGE,
        SEARCH_FAILED,
        WRITE_SCOPE_DENIED,
        SYMBOLIC_LINK_DENIED,
        REPLACEMENT_NOT_FOUND,
        REPLACEMENT_NOT_UNIQUE,
        CONTENT_TOO_LARGE,
        RESULT_TOO_LARGE,
        WORKTREE_UNAVAILABLE
    }
}
