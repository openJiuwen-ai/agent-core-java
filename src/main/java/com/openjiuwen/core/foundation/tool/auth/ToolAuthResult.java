/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool authentication result.
 * <p>
 * Mirrors Python's {@code ToolAuthResult} dataclass from
 * <code>foundation/tool/auth/auth.py</code>.
 *
 * @param success   whether authentication was successful
 * @param authData  authentication data (headers, ssl config, credentials, etc.)
 * @param message   authentication message
 * @param error     authentication error, if any
 */
public class ToolAuthResult {

    private final boolean success;
    private final Map<String, Object> authData;
    private final String message;
    private final Exception error;

    public ToolAuthResult(boolean success, Map<String, Object> authData, String message) {
        this(success, authData, message, null);
    }

    public ToolAuthResult(boolean success, Map<String, Object> authData, String message, Exception error) {
        this.success = success;
        this.authData = authData != null ? new HashMap<>(authData) : new HashMap<>();
        this.message = message != null ? message : "";
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getAuthData() {
        return Collections.unmodifiableMap(authData);
    }

    public String getMessage() {
        return message;
    }

    public Exception getError() {
        return error;
    }
}
