/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

/**
 * Abstract base class for authentication strategies.
 * <p>
 * Mirrors Python's {@code AuthStrategy} ABC from
 * <code>foundation/tool/auth/auth_callback.py</code>.
 *
 * <p>Subclasses must define their {@link AuthType} and implement
 * the {@link #authenticate(ToolAuthConfig)} method.
 */
public abstract class AuthStrategy {

    /** The authentication type this strategy handles. */
    protected final AuthType authType;

    protected AuthStrategy(AuthType authType) {
        this.authType = authType;
    }

    public AuthType getAuthType() {
        return authType;
    }

    /**
     * Execute authentication with the given configuration.
     *
     * @param authConfig the authentication configuration
     * @return the authentication result
     */
    public abstract ToolAuthResult authenticate(ToolAuthConfig authConfig);
}
