/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.workflow.component.tool.http.HTTPRequestComponent;
import com.openjiuwen.core.workflow.component.tool.http.HTTPRequestExecutable;
import com.openjiuwen.core.workflow.component.tool.http.HttpAdvancedOptionsConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpComponentConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRequestParamConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRetryConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_http_component} in
 * {@code tests.unit_tests.core.component.test_http_component}.
 */
@Tag("unit-test")
class TestHttpComponent {

    @Test
    @DisplayName("component creation preserves request config")
    void testComponentCreation() {
        HttpComponentConfig config = new HttpComponentConfig(HttpRequestParamConfig.builder()
                .url("https://httpbin.org/get")
                .method("GET")
                .build());

        HTTPRequestComponent component = new HTTPRequestComponent(config);

        assertNotNull(component);
        assertEquals("https://httpbin.org/get", component.getConfig().getRequestParams().getUrl());
    }

    @Test
    @DisplayName("component creates executable lazily")
    void testExecutableCreation() {
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/get")
                        .method("GET")
                        .build()));

        assertInstanceOf(HTTPRequestExecutable.class, component.getExecutable());
        assertEquals(component.getExecutable(), component.getExecutable());
    }

    @Test
    @DisplayName("Start -> HTTPRequest -> End equivalent executes against local HTTP server")
    void testStartHttpRequestEndInWorkflow() throws Exception {
        HttpServer server = startServer("/get", 200, "{\"test\":\"value\"}");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/get";
            HTTPRequestExecutable executable = new HTTPRequestExecutable(new HttpComponentConfig(
                    HttpRequestParamConfig.builder()
                            .url("{{url}}")
                            .method("GET")
                            .advancedOptions(HttpAdvancedOptionsConfig.builder().ignoreSslIssues(true).build())
                            .build()));

            Object result = executable.invoke(Map.of("url", url), null, null);

            assertInstanceOf(Map.class, result);
            Map<?, ?> response = (Map<?, ?>) result;
            assertEquals(200, response.get("statusCode"));
            assertEquals("{\"test\":\"value\"}", response.get("body"));
            assertEquals(Boolean.TRUE, response.get("ok"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("retry and timeout config is preserved before execution")
    void testRetryCountAndTimeoutSessionHandling() {
        HttpRetryConfig retryConfig = HttpRetryConfig.builder()
                .enabled(true)
                .maxRetries(2)
                .retryDelay(100)
                .retryOnStatusCodes(List.of(500, 502, 503, 504, 429))
                .backoffType("fixed")
                .build();
        HttpAdvancedOptionsConfig advancedOptions = HttpAdvancedOptionsConfig.builder()
                .timeout(10000)
                .ignoreSslIssues(true)
                .build();
        HttpRequestParamConfig params = HttpRequestParamConfig.builder()
                .url("{{url}}")
                .method("GET")
                .timeout(5.0)
                .advancedOptions(advancedOptions)
                .retryConfig(retryConfig)
                .build();

        assertEquals(5.0, params.getTimeout());
        assertEquals(10000, params.getAdvancedOptions().getTimeout());
        assertTrue(params.getRetryConfig().isEnabled());
        assertEquals(2, params.getRetryConfig().getMaxRetries());
        assertEquals(100, params.getRetryConfig().getRetryDelay());
        assertTrue(params.getRetryConfig().getRetryOnStatusCodes().contains(500));
    }

    @Test
    @DisplayName("stream delegates to invoke for HTTP output")
    void testExecutableStream() throws Exception {
        HttpServer server = startServer("/stream", 200, "ok");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream";
            HTTPRequestExecutable executable = new HTTPRequestExecutable(new HttpComponentConfig(
                    HttpRequestParamConfig.builder().url(url).method("GET").build()));

            Iterator<Object> stream = executable.stream(Map.of(), null, null);

            assertTrue(stream.hasNext());
            assertEquals("ok", ((Map<?, ?>) stream.next()).get("body"));
            assertFalse(stream.hasNext());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for local weather timeout service")
    void testHttpComp008() {
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for local weather service")
    void testHttpComp002() {
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for local POST weather service")
    void testHttpComp013() {
    }

    private static HttpServer startServer(String path, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> writeResponse(exchange, status, body));
        server.start();
        return server;
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }
}
