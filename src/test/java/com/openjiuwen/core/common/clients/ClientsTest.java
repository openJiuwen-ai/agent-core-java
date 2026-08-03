/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/core/common/clients/__init__.py}.
 *
 * <p>Mirrors Python's HTTP session exports from
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 *
 * <p>Mirrors Python's HTTPX connector exports from
 * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
 */
class ClientsTest {

    private static final Logger CONNECTOR_POOL_LOGGER =
            Logger.getLogger("com.openjiuwen.core.common.clients.ConnectorPoolManager");

    static {
        CONNECTOR_POOL_LOGGER.setUseParentHandlers(false);
        CONNECTOR_POOL_LOGGER.setLevel(Level.OFF);
    }

    @Test
    void exportedNamesMatchPythonAllOrder() {
        assertThat(Clients.exportedNames()).containsExactly(
                "get_client_registry",
                "BaseClient",
                "get_connector_pool_manager",
                "ConnectorPool",
                "ConnectorPoolConfig",
                "HttpXConnectorPoolConfig",
                "SessionConfig",
                "get_http_session_manager"
        );
    }

    @Test
    void typedExportsExposeAvailableJavaTypesAndSingletons() {
        assertThat(Clients.typedExportNames()).containsExactly(
                "get_client_registry",
                "BaseClient",
                "get_connector_pool_manager",
                "ConnectorPool",
                "ConnectorPoolConfig",
                "HttpXConnectorPoolConfig",
                "SessionConfig",
                "get_http_session_manager"
        );
        assertThat(Clients.getClientRegistry()).isSameAs(ClientRegistry.getClientRegistry());
        assertThat(Clients.baseClientClass()).isEqualTo(BaseClient.class);
        assertThat(Clients.getConnectorPoolManager()).isSameAs(ConnectorPoolManager.getConnectorPoolManager());
        assertThat(Clients.connectorPoolClass()).isEqualTo(ConnectorPool.class);
        assertThat(Clients.connectorPoolConfigClass()).isEqualTo(ConnectorPoolConfig.class);
        assertThat(Clients.httpXConnectorPoolConfigClass()).isEqualTo(HttpXConnectorPoolConfig.class);
        assertThat(Clients.sessionConfigClass()).isEqualTo(SessionConfig.class);
        assertThat(Clients.getHttpSessionManager()).isNotNull();
    }

    @Test
    void laterBatchExportsRemainListedWithoutNonCompilingTypeReferences() {
        assertThat(Clients.futureTypedExportNames()).isEmpty();
        assertThat(Clients.isExported("SessionConfig")).isTrue();
        assertThat(Clients.isExported("unknown")).isFalse();
    }

    @Test
    void requireExportedRejectsUnknownNames() {
        Clients.requireExported("BaseClient");

        assertThatThrownBy(() -> Clients.requireExported("MissingClient"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name is not exported by openjiuwen.core.common.clients");
    }
}
