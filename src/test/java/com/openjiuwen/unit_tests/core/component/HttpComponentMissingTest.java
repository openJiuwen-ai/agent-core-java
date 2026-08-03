/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.component;

import com.openjiuwen.core.workflow.component.tool.http.HTTPRequestComponent;
import com.openjiuwen.core.workflow.component.tool.http.HttpAdvancedOptionsConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpComponentConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRequestParamConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRetryConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_http_component.py}.</p>
 */
class HttpComponentMissingTest {

    @Test
    void testComponentCreation() {
        HttpComponentConfig config = config("https://httpbin.org/get", "GET");

        HTTPRequestComponent component = new HTTPRequestComponent(config);

        assertThat(component).isNotNull();
        assertThat(component.getConfig().getRequestParams().getUrl()).isEqualTo("https://httpbin.org/get");
    }

    @Test
    void testExecutableCreation() {
        HttpComponentConfig config = config("https://httpbin.org/get", "GET");

        HTTPRequestComponent component = new HTTPRequestComponent(config);

        assertThat(component.getExecutable()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStartHttpRequestEndInWorkflow() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> respond(exchange, 200, "{\"ok\":true}"))) {
            HttpComponentConfig config = config(server.url("/get?test=value"), "GET");
            HTTPRequestComponent component = new HTTPRequestComponent(config);

            Object result = component.getExecutable().invoke(Map.of(), null, null);

            assertThat(result).isNotNull();
            Map<String, Object> output = (Map<String, Object>) result;
            assertThat(output).containsEntry("statusCode", 200).containsEntry("ok", true);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRetryCountAndTimeoutSessionHandling() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 500, "Internal Server Error");
        })) {
            HttpComponentConfig config = new HttpComponentConfig();
            config.setRequestParams(HttpRequestParamConfig.builder()
                    .url(server.url("/status/500"))
                    .method("GET")
                    .timeout(5.0)
                    .advancedOptions(HttpAdvancedOptionsConfig.builder()
                            .timeout(10000)
                            .ignoreSslIssues(true)
                            .build())
                    .retryConfig(HttpRetryConfig.builder()
                            .enabled(true)
                            .maxRetries(2)
                            .retryDelay(1)
                            .retryOnStatusCodes(java.util.List.of(500, 502, 503, 504, 429))
                            .backoffType("fixed")
                            .build())
                    .build());
            HTTPRequestComponent component = new HTTPRequestComponent(config);

            Object result = component.getExecutable().invoke(Map.of(), null, null);

            assertThat(config.getRequestParams().getTimeout()).isEqualTo(5.0);
            assertThat(config.getRequestParams().getAdvancedOptions().getTimeout()).isEqualTo(10000);
            assertThat(config.getRequestParams().getRetryConfig().isEnabled()).isTrue();
            assertThat(config.getRequestParams().getRetryConfig().getMaxRetries()).isEqualTo(2);
            assertThat(requestCount).hasValue(3);
            Map<String, Object> output = (Map<String, Object>) result;
            assertThat(output).containsEntry("statusCode", 500).containsEntry("ok", false);
        }
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testHttpComp008() {
        assertThat(true).isTrue();
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testHttpComp002() {
        assertThat(true).isTrue();
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testHttpComp013() {
        assertThat(true).isTrue();
    }

    private static HttpComponentConfig config(String url, String method) {
        HttpComponentConfig config = new HttpComponentConfig();
        config.setRequestParams(HttpRequestParamConfig.builder()
                .url(url)
                .method(method)
                .build());
        return config;
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;

        private LocalHttpServer(HttpServer server) {
            this.server = server;
        }

        private static LocalHttpServer start(ExchangeHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> handler.handle(exchange));
            server.start();
            return new LocalHttpServer(server);
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
