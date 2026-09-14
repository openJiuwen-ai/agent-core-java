/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and executor for authentication strategies.
 *
 * <p>Mirrors Python's {@code AuthStrategyRegistry} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public final class AuthStrategyRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AuthStrategyRegistry.class);
    private static final Map<AuthType, Class<? extends AuthStrategy>> STRATEGIES = new ConcurrentHashMap<>();

    static {
        register(SSLAuthStrategy.class);
        register(HeaderQueryAuthStrategy.class);
    }

    private AuthStrategyRegistry() {
    }

    public static void register(Class<? extends AuthStrategy> strategyClass) {
        try {
            AuthStrategy strategy = strategyClass.getDeclaredConstructor().newInstance();
            STRATEGIES.put(strategy.getAuthType(), strategyClass);
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Failed to register auth strategy: " + strategyClass.getName(), error);
        }
    }

    public static ToolAuthResult executeAuth(ToolAuthConfig authConfig) {
        AuthType authType = AuthType.fromValue(authConfig.getAuthType());
        Class<? extends AuthStrategy> strategyClass = authType == null ? null : STRATEGIES.get(authType);
        if (strategyClass == null) {
            LOG.warn("Unsupported auth type: {}", authConfig.getAuthType());
            return new ToolAuthResult(
                    false,
                    new LinkedHashMap<>(),
                    "Unsupported auth type: " + authConfig.getAuthType(),
                    null
            );
        }
        try {
            AuthStrategy strategy = strategyClass.getDeclaredConstructor().newInstance();
            return strategy.authenticate(authConfig);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to execute auth strategy: " + strategyClass.getName(), error);
        }
    }
}
