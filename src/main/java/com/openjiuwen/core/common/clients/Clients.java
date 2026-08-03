/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.clients.http.HttpSessionManager;

import java.util.List;

/**
 * Package export facade for common clients.
 *
 * <p>Mirrors Python's {@code __all__} exports in
 * {@code openjiuwen/core/common/clients/__init__.py}.</p>
 *
 * <p>Mirrors Python's HTTP session exports from
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 *
 * <p>Mirrors Python's HTTPX connector exports from
 * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
 */
public final class Clients {

    private static final List<String> EXPORTED_NAMES = List.of(
            "get_client_registry",
            "BaseClient",
            "get_connector_pool_manager",
            "ConnectorPool",
            "ConnectorPoolConfig",
            "HttpXConnectorPoolConfig",
            "SessionConfig",
            "get_http_session_manager"
    );

    private static final List<String> TYPED_EXPORT_NAMES = List.of(
            "get_client_registry",
            "BaseClient",
            "get_connector_pool_manager",
            "ConnectorPool",
            "ConnectorPoolConfig",
            "HttpXConnectorPoolConfig",
            "SessionConfig",
            "get_http_session_manager"
    );

    private static final List<String> FUTURE_TYPED_EXPORT_NAMES = List.of();

    private Clients() {
    }

    public static List<String> exportedNames() {
        return EXPORTED_NAMES;
    }

    public static List<String> typedExportNames() {
        return TYPED_EXPORT_NAMES;
    }

    public static List<String> futureTypedExportNames() {
        return FUTURE_TYPED_EXPORT_NAMES;
    }

    public static boolean isExported(String name) {
        return EXPORTED_NAMES.contains(name);
    }

    public static void requireExported(String name) {
        if (!isExported(name)) {
            throw new IllegalArgumentException("Name is not exported by openjiuwen.core.common.clients: " + name);
        }
    }

    public static ClientRegistry getClientRegistry() {
        return ClientRegistry.getClientRegistry();
    }

    public static Class<BaseClient> baseClientClass() {
        return BaseClient.class;
    }

    public static ConnectorPoolManager getConnectorPoolManager() {
        return ConnectorPoolManager.getConnectorPoolManager();
    }

    public static Class<ConnectorPool> connectorPoolClass() {
        return ConnectorPool.class;
    }

    public static Class<ConnectorPoolConfig> connectorPoolConfigClass() {
        return ConnectorPoolConfig.class;
    }

    public static Class<HttpXConnectorPoolConfig> httpXConnectorPoolConfigClass() {
        return HttpXConnectorPoolConfig.class;
    }

    public static Class<SessionConfig> sessionConfigClass() {
        return SessionConfig.class;
    }

    public static HttpSessionManager getHttpSessionManager() {
        return HttpSessionManager.getHttpSessionManager();
    }
}
