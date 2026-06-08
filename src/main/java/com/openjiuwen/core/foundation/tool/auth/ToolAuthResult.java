/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Tool authentication result.
 *
 * <p>Mirrors Python's {@code ToolAuthResult} in
 * {@code openjiuwen/core/foundation/tool/auth/auth.py}.
 */
public final class ToolAuthResult {

    private final boolean success;
    private final Map<String, Object> authData;
    private final String message;
    private final Exception error;

    @JsonCreator
    public ToolAuthResult(
            @JsonProperty("success") boolean success,
            @JsonProperty("auth_data") Map<String, Object> authData,
            @JsonProperty("message") String message,
            @JsonProperty("error") Exception error
    ) {
        this.success = success;
        this.authData = authData == null ? Map.of() : Map.copyOf(authData);
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getAuthData() {
        return authData;
    }

    public String getMessage() {
        return message;
    }

    public Exception getError() {
        return error;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolAuthResult that)) {
            return false;
        }
        return success == that.success
                && Objects.equals(authData, that.authData)
                && Objects.equals(message, that.message)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, authData, message, error);
    }
}
