/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

/**
 * Base class for tool authentication strategies.
 *
 * <p>Mirrors Python's {@code AuthStrategy} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public abstract class AuthStrategy {

    private final AuthType authType;

    protected AuthStrategy(AuthType authType) {
        this.authType = authType;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public abstract ToolAuthResult authenticate(ToolAuthConfig authConfig);
}
