/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and factory helpers for server adapters.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/core/runner/drunner/server_adapter/__init__.py}.</p>
 */
public final class ServerAdapterRegistry {

    private static final Map<String, ServerAdapterFactory> CUSTOM_SERVER_ADAPTERS = new ConcurrentHashMap<>();
    private static final Map<String, String> OFFICIAL_SERVER_ADAPTER_BOOTSTRAP = Map.of(
            "A2A", "com.openjiuwen.extensions.a2a.A2ABootstrap"
    );

    private ServerAdapterRegistry() {
    }

    public static void registerServerAdapter(String name, ServerAdapterFactory factory) {
        CUSTOM_SERVER_ADAPTERS.put(name, factory);
    }

    public static Object createServerAdapter(String protocol, Map<String, Object> kwargs) {
        if (!CUSTOM_SERVER_ADAPTERS.containsKey(protocol)) {
            bootstrapOfficialServerAdapter(protocol);
        }
        ServerAdapterFactory factory = CUSTOM_SERVER_ADAPTERS.get(protocol);
        if (factory != null) {
            return factory.create(kwargs);
        }
        return resolveProvider(protocol, kwargs);
    }

    private static void bootstrapOfficialServerAdapter(String protocol) {
        String className = OFFICIAL_SERVER_ADAPTER_BOOTSTRAP.get(protocol);
        if (className == null) {
            return;
        }
        try {
            Class.forName(className);
        } catch (Exception ignored) {
            // Optional plugin bootstrap is best-effort.
        }
    }

    private static Object resolveProvider(String protocol, Map<String, Object> kwargs) {
        try {
            for (ServerAdapterProvider provider : ServiceLoader.load(ServerAdapterProvider.class)) {
                if (protocol.equals(provider.protocol())) {
                    return provider.create(new LinkedHashMap<>(kwargs));
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
