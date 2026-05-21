/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and executor for authentication strategies.
 * <p>
 * Mirrors Python's {@code AuthStrategyRegistry} class from
 * <code>foundation/tool/auth/auth_callback.py</code>.
 *
 * <p>Manages registration of authentication strategies and provides
 * a unified entry point for executing authentication.
 *
 * <p>Strategies are auto-registered at class load time:
 * <ul>
 *   <li>{@link SSLAuthStrategy}</li>
 *   <li>{@link HeaderQueryAuthStrategy}</li>
 * </ul>
 */
public class AuthStrategyRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AuthStrategyRegistry.class);

    private static final Map<AuthType, Class<? extends AuthStrategy>> strategies = new ConcurrentHashMap<>();

    static {
        register(SSLAuthStrategy.class);
        register(HeaderQueryAuthStrategy.class);
    }

    /**
     * Register an authentication strategy class.
     *
     * @param strategyClass the strategy class to register
     */
    public static void register(Class<? extends AuthStrategy> strategyClass) {
        try {
            AuthStrategy instance = strategyClass.getDeclaredConstructor().newInstance();
            strategies.put(instance.getAuthType(), strategyClass);
        } catch (Exception e) {
            LOG.error("Failed to register auth strategy: {} - {}", strategyClass.getName(), e.getMessage());
        }
    }

    /**
     * Execute authentication using the strategy matching the config's auth type.
     *
     * @param authConfig the authentication configuration
     * @return the authentication result, or a failure result if no strategy matches
     */
    public static ToolAuthResult executeAuth(ToolAuthConfig authConfig) {
        AuthType authType = AuthType.fromValue(authConfig.getAuthType());
        if (authType == null) {
            LOG.warn("Unsupported auth type: {}", authConfig.getAuthType());
            return new ToolAuthResult(false, new HashMap<>(),
                    "Unsupported auth type: " + authConfig.getAuthType());
        }

        Class<? extends AuthStrategy> strategyClass = strategies.get(authType);
        if (strategyClass == null) {
            LOG.warn("No strategy registered for auth type: {}", authType);
            return new ToolAuthResult(false, new HashMap<>(),
                    "No strategy registered for auth type: " + authType);
        }

        try {
            AuthStrategy strategy = strategyClass.getDeclaredConstructor().newInstance();
            return strategy.authenticate(authConfig);
        } catch (Exception e) {
            LOG.error("Failed to execute auth strategy: {}", e.getMessage());
            return new ToolAuthResult(false, new HashMap<>(),
                    "Failed to execute auth strategy: " + e.getMessage(), e);
        }
    }
}
