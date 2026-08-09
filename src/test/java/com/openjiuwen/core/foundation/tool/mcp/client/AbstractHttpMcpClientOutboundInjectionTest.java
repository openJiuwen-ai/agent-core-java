/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;

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

    private static final class StubHttpMcpClient extends AbstractHttpMcpClient {
        private StubHttpMcpClient(McpServerConfig config) {
            super(config);
        }
    }
}
