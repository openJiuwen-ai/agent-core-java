/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Header and query parameter authentication strategy.
 * <p>
 * Mirrors Python's {@code HeaderQueryAuthStrategy} class from
 * <code>foundation/tool/auth/auth_callback.py</code>.
 *
 * <p>Configures custom headers and query parameters for HTTP requests
 * based on the authentication configuration.
 */
public class HeaderQueryAuthStrategy extends AuthStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderQueryAuthStrategy.class);

    public HeaderQueryAuthStrategy() {
        super(AuthType.HEADER_AND_QUERY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolAuthResult authenticate(ToolAuthConfig authConfig) {
        Map<String, Object> config = authConfig.getConfig();

        Map<String, String> authHeaders = (Map<String, String>) config.get("auth_headers");
        Map<String, String> authQueryParams = (Map<String, String>) config.get("auth_query_params");

        if (authHeaders == null) {
            authHeaders = new HashMap<>();
        }
        if (authQueryParams == null) {
            authQueryParams = new HashMap<>();
        }

        boolean hasCustomAuth = !authHeaders.isEmpty() || !authQueryParams.isEmpty();

        Map<String, Object> authData = new HashMap<>();
        if (hasCustomAuth) {
            authData.put("auth_headers", authHeaders);
            authData.put("auth_query_params", authQueryParams);
            LOG.info("Using custom header and query authorization for {}", authConfig.getToolType());
        }

        return new ToolAuthResult(true, authData,
                hasCustomAuth ? "Custom header and query authentication configured"
                        : "No custom auth headers or query params provided");
    }
}
