/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies runtime outbound injection of {@code _ojw_http_client} on HTTP MCP clients.
 */
class AbstractHttpMcpClientOutboundInjectionTest {

    @Test
    void usesInjectedHttpClientFromParams() {
        HttpClient injected = HttpClient.newHttpClient();
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("http://127.0.0.1:9/mcp")
                .params(Map.of("_ojw_http_client", injected))
                .build();

        StubHttpMcpClient client = new StubHttpMcpClient(config);

        assertThat(client.httpClient).isSameAs(injected);
    }

    @Test
    void createsDefaultHttpClientWhenNotInjected() {
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("http://127.0.0.1:9/mcp")
                .build();

        StubHttpMcpClient client = new StubHttpMcpClient(config);

        assertThat(client.httpClient).isNotNull();
    }

    @Test
    void connectLeavesDisconnectedWhenHandshakeFails() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("http://127.0.0.1:9/mcp")
                .build();
        FailingHandshakeClient client = new FailingHandshakeClient(config, 99);

        assertThat(client.connect(0, 1.0F)).isFalse();
        assertThat(client.connected).isFalse();
        assertThat(client.rpcCalls).isEqualTo(1);
    }

    @Test
    void connectMarksConnectedAfterSuccessfulRetry() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("http://127.0.0.1:9/mcp")
                .build();
        FailingHandshakeClient client = new FailingHandshakeClient(config, 1);

        assertThat(client.connect(1, 1.0F)).isTrue();
        assertThat(client.connected).isTrue();
        assertThat(client.rpcCalls).isEqualTo(2);
    }

    private static final class StubHttpMcpClient extends AbstractHttpMcpClient {
        private StubHttpMcpClient(McpServerConfig config) {
            super(config);
        }
    }

    private static final class FailingHandshakeClient extends AbstractHttpMcpClient {
        private final int failTimes;
        private int rpcCalls;

        private FailingHandshakeClient(McpServerConfig config, int failTimes) {
            super(config);
            this.failTimes = failTimes;
        }

        @Override
        Map<String, Object> executeRpc(String method, Map<String, Object> params, float timeout)
                throws IOException {
            rpcCalls++;
            if (rpcCalls <= failTimes) {
                throw new IOException("handshake failed");
            }
            return Map.of();
        }

        @Override
        void executeNotification(String method, Map<String, Object> params, float timeout) {
            // Handshake notification is a no-op in tests.
        }
    }
}
