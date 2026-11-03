/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header and query parameter authentication strategy.
 *
 * <p>Mirrors Python's {@code HeaderQueryAuthStrategy} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public class HeaderQueryAuthStrategy extends AuthStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderQueryAuthStrategy.class);

    public HeaderQueryAuthStrategy() {
        super(AuthType.HEADER_AND_QUERY);
    }

    @Override
    public ToolAuthResult authenticate(ToolAuthConfig authConfig) {
        Map<String, Object> config = authConfig.getConfig();
        boolean hasHeaderKey = config.containsKey("auth_headers") && config.get("auth_headers") != null;
        boolean hasQueryKey = config.containsKey("auth_query_params") && config.get("auth_query_params") != null;
        AuthHeaderAndQueryProvider authProvider = null;
        if (hasHeaderKey || hasQueryKey) {
            authProvider = new AuthHeaderAndQueryProvider(
                    toStringMap(config.get("auth_headers")),
                    toStringMap(config.get("auth_query_params"))
            );
            LOG.info("Using custom header and query authorization for {}", authConfig.getToolType());
        }
        Map<String, Object> authData = new LinkedHashMap<>();
        authData.put("auth_provider", authProvider);
        return new ToolAuthResult(
                true,
                authData,
                "Custom header and query authentication configured",
                null
        );
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return converted;
    }
}
