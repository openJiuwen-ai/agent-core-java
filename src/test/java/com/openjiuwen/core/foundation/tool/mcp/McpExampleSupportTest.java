package com.openjiuwen.core.foundation.tool.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpExampleSupportTest {

    @Test
    void shouldBuildConfigsForAllExampleTransports() {
        McpServerConfig http = McpExampleSupport.streamableHttpConfig("runtime-http", "127.0.0.1", 8940, "mcp");
        McpServerConfig sse = McpExampleSupport.sseConfig("runtime-sse", "http://127.0.0.1:8930/sse");
        McpServerConfig stdio = McpExampleSupport.stdioConfig("runtime-stdio", ".", List.of("-m", "server"));
        McpServerConfig openapi = McpExampleSupport.openApiConfig("runtime-openapi", "http://127.0.0.1:8080/openapi.yaml");
        McpServerConfig playwright = McpExampleSupport.playwrightConfig("runtime-playwright", "stdio://playwright");

        assertThat(http.getClientType()).isEqualTo("streamable-http");
        assertThat(http.getServerPath()).isEqualTo("http://127.0.0.1:8940/mcp");
        assertThat(sse.getClientType()).isEqualTo("sse");
        assertThat(stdio.getParams()).containsEntry("cwd", ".");
        assertThat(stdio.getParams().get("args")).isEqualTo(List.of("-m", "server"));
        assertThat(openapi.getClientType()).isEqualTo("openapi");
        assertThat(playwright.getClientType()).isEqualTo("playwright");
    }

    @Test
    void describeShouldProduceStableSummary() {
        McpServerConfig config = McpExampleSupport.streamableHttpConfig("runtime-http", "127.0.0.1", 8940, "mcp");

        assertThat(McpExampleSupport.describe(config))
                .isEqualTo("server=runtime-http, clientType=streamable-http, path=http://127.0.0.1:8940/mcp");
    }
}
