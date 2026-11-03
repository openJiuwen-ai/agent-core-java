/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Unified authentication handler and registration helper.
 *
 * <p>Mirrors Python's {@code unified_auth_handler} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public final class AuthCallback {

    private AuthCallback() {
    }

    public static ToolAuthResult unifiedAuthHandler(ToolAuthConfig authConfig) {
        return AuthStrategyRegistry.executeAuth(authConfig);
    }

    public static ToolAuthResult unifiedAuthHandler(Map<String, Object> kwargs) {
        return AuthStrategyRegistry.executeAuth(extractAuthConfig(kwargs));
    }

    public static void registerWith(DecoratorFramework framework) {
        Function<Map<String, Object>, Object> callback = kwargs -> unifiedAuthHandler(kwargs);
        framework.registerSync(
                ToolCallEvents.TOOL_AUTH,
                callback,
                0,
                false,
                "default",
                Set.of("tool-auth"),
                Collections.emptyList(),
                null,
                null,
                0,
                0.0d,
                null,
                "unified_auth_handler"
        );
    }

    private static ToolAuthConfig extractAuthConfig(Map<String, Object> kwargs) {
        Object authConfig = kwargs == null ? null : kwargs.get("auth_config");
        if (authConfig instanceof ToolAuthConfig typedConfig) {
            return typedConfig;
        }
        Object args = kwargs == null ? null : kwargs.get("_args");
        if (args instanceof Object[] values && values.length > 0 && values[0] instanceof ToolAuthConfig typedConfig) {
            return typedConfig;
        }
        throw new IllegalArgumentException("auth_config is required for tool authentication");
    }
}
