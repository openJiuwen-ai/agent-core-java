/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for custom headers in LLM client.
 * <p>
 * Mirrors Python's {@code test_custom_headers_system.py} in
 * {@code tests.system_tests.foundation.llm}.
 */
class TestCustomHeadersSystem {

    @Test
    void testModelInvokeInjectsSanitizedConfigHeaders() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.MODEL,
                headers("Token", "token-a", "UserID", "user-a", "Authorization", "blocked", "X-None", null),
                null);

        assertEffectiveHeaders(sent, Map.of("Token", "token-a", "UserID", "user-a"));
        assertThat(sent.captured().authorization()).isEqualTo("Bearer sk-test");
    }

    @Test
    void testModelInvokeRequestHeadersOverrideCaseInsensitive() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.MODEL,
                headers("X-Tenant", "tenant-cfg", "UserID", "user-cfg"),
                headers("x-tenant", "tenant-req", "userid", "user-req", "Connection", "blocked"));

        assertEffectiveHeaders(sent, Map.of("X-Tenant", "tenant-req", "UserID", "user-req"));
    }

    @Test
    void testModelInvokeWithoutHeadersHasNoExtraHeaders() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.MODEL, null, null);

        assertNoExtraHeaders(sent);
    }

    @Test
    void testModelStreamInjectsSanitizedConfigHeaders() throws Exception {
        SentParams sent = streamAndGetSentParams(ModelBuildMode.MODEL,
                headers("UserID", "stream-cfg", "Host", "blocked"),
                null);

        assertEffectiveHeaders(sent, Map.of("UserID", "stream-cfg"));
        assertThat(findHeaderValue(sent.captured().headers(), "Host")).isNotEqualTo("blocked");
    }

    @Test
    void testModelStreamRequestHeadersOverride() throws Exception {
        SentParams sent = streamAndGetSentParams(ModelBuildMode.MODEL,
                headers("Token", "cfg", "UserID", "cfg-user"),
                headers("token", "req", "userid", "req-user", "Authorization", "blocked"));

        assertEffectiveHeaders(sent, Map.of("Token", "req", "UserID", "req-user"));
        assertThat(sent.captured().authorization()).isEqualTo("Bearer sk-test");
    }

    @Test
    void testInitModelInvokeInjectsHeaders() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.INIT_MODEL,
                headers("Token", "init-token", "Content-Length", "blocked"),
                null);

        assertEffectiveHeaders(sent, Map.of("Token", "init-token"));
    }

    @Test
    void testInitModelStreamRequestHeadersOverride() throws Exception {
        SentParams sent = streamAndGetSentParams(ModelBuildMode.INIT_MODEL,
                headers("UserID", "init-user"),
                headers("userid", "init-user-req", "Transfer-Encoding", "blocked"));

        assertEffectiveHeaders(sent, Map.of("UserID", "init-user-req"));
    }

    @Test
    void testReactConfigInvokeInjectsHeaders() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.REACT_CONFIG,
                headers("Token", "react-token", "Connection", "blocked"),
                null);

        assertEffectiveHeaders(sent, Map.of("Token", "react-token"));
    }

    @Test
    void testReactConfigStreamRequestHeadersOverride() throws Exception {
        SentParams sent = streamAndGetSentParams(ModelBuildMode.REACT_CONFIG,
                headers("UserID", "react-cfg-user"),
                headers("userid", "react-req-user", "Host", "blocked"));

        assertEffectiveHeaders(sent, Map.of("UserID", "react-req-user"));
        assertThat(findHeaderValue(sent.captured().headers(), "Host")).isNotEqualTo("blocked");
    }

    @Test
    void testReactConfigWithoutHeadersHasNoExtraHeaders() throws Exception {
        SentParams sent = invokeAndGetSentParams(ModelBuildMode.REACT_CONFIG, null, null);

        assertNoExtraHeaders(sent);
    }

    @Test
    void testCommonAsyncOpenaiClientForwardsSanitizedDefaultHeaders() {
        ModelClientConfig config = baseClientConfig("https://api.openai.com/v1",
                headers("Token", "token-x", "Authorization", "blocked", "X-Blank", " "));

        assertThat(HeadersHelper.buildBaseHeaders(config.getCustomHeaders()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("Token", "token-x"));
    }

    @Test
    void testCommonSyncOpenaiClientForwardsSanitizedDefaultHeaders() {
        ModelClientConfig config = baseClientConfig("https://api.openai.com/v1",
                headers("UserID", "user-x", "Content-Length", "blocked", "X-None", null));

        assertThat(HeadersHelper.buildBaseHeaders(config.getCustomHeaders()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("UserID", "user-x"));
    }

    @Test
    void testCommonOpenaiClientWithoutCustomHeadersOmitsDefaultHeaders() {
        ModelClientConfig config = baseClientConfig("https://api.openai.com/v1", null);

        assertThat(HeadersHelper.buildBaseHeaders(config.getCustomHeaders())).isEmpty();
    }

    private SentParams invokeAndGetSentParams(ModelBuildMode mode,
                                              Map<String, Object> customHeaders,
                                              Map<String, Object> requestHeaders) throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = createServer(captured, false);
        server.start();
        try {
            Model model = buildModel(mode, server, customHeaders);
            RecordingTracer tracer = new RecordingTracer();
            AssistantMessage response = model.invoke(
                    List.of(new UserMessage("hello")),
                    null, null, null, null, null, null, null, null,
                    invokeKwargs(tracer, requestHeaders));
            assertThat(response.getContentAsString()).isEqualTo("ok");
            return new SentParams(extraHeadersFrom(tracer), captured.snapshot());
        } finally {
            server.stop(0);
        }
    }

    private SentParams streamAndGetSentParams(ModelBuildMode mode,
                                              Map<String, Object> customHeaders,
                                              Map<String, Object> requestHeaders) throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = createServer(captured, true);
        server.start();
        try {
            Model model = buildModel(mode, server, customHeaders);
            RecordingTracer tracer = new RecordingTracer();
            Iterator<AssistantMessageChunk> chunks = model.stream(
                    List.of(new UserMessage("hello")),
                    null, null, null, null, null, null, null, null,
                    invokeKwargs(tracer, requestHeaders));
            while (chunks.hasNext()) {
                chunks.next();
            }
            return new SentParams(extraHeadersFrom(tracer), captured.snapshot());
        } finally {
            server.stop(0);
        }
    }

    private Model buildModel(ModelBuildMode mode, HttpServer server, Map<String, Object> customHeaders) {
        String apiBase = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        if (mode == ModelBuildMode.REACT_CONFIG) {
            ReActAgentConfig config = new ReActAgentConfig();
            config.configureCustomHeaders(customHeaders);
            config.configureModelClient(
                    ProviderType.OpenAI.getValue(),
                    "sk-test",
                    apiBase,
                    "gpt-4o-mini",
                    false);
            return new Model(config.getModelClientConfig(), config.getModelConfigObj());
        }

        return new Model(
                baseClientConfig(apiBase, customHeaders),
                ModelRequestConfig.builder()
                        .modelName("gpt-4o-mini")
                        .build());
    }

    private ModelClientConfig baseClientConfig(String apiBase, Map<String, Object> customHeaders) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase(apiBase)
                .verifySsl(false)
                .customHeaders(customHeaders)
                .build();
    }

    private HttpServer createServer(CapturedRequest captured, boolean stream) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.capture(exchange);
            if (stream) {
                writeText(exchange, "text/event-stream",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                                + "data: [DONE]\n\n");
            } else {
                writeText(exchange, "application/json",
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}");
            }
        });
        return server;
    }

    private Map<String, Object> invokeKwargs(RecordingTracer tracer, Map<String, Object> requestHeaders) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tracer_record_data", tracer);
        if (requestHeaders != null) {
            kwargs.put("custom_headers", requestHeaders);
        }
        return kwargs;
    }

    private void assertEffectiveHeaders(SentParams sent, Map<String, String> expected) {
        assertThat(sent.extraHeaders()).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(sent.extraHeaders()).doesNotContainKeys(
                "Authorization", "Connection", "Host", "Content-Length", "Transfer-Encoding");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertThat(findHeaderValue(sent.captured().headers(), entry.getKey())).isEqualTo(entry.getValue());
        }
        assertThat(sent.captured().body()).doesNotContain("extra_headers");
    }

    private void assertNoExtraHeaders(SentParams sent) {
        assertThat(sent.extraHeaders()).isEmpty();
        assertThat(sent.captured().token()).isEmpty();
        assertThat(sent.captured().userId()).isEmpty();
        assertThat(sent.captured().body()).doesNotContain("extra_headers");
    }

    private static Map<String, Object> headers(Object... keyValues) {
        Map<String, Object> headers = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            headers.put((String) keyValues[i], keyValues[i + 1]);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extraHeadersFrom(RecordingTracer tracer) {
        for (Map<String, Object> record : tracer.records()) {
            Object params = record.get("llm_params");
            if (params instanceof Map<?, ?> paramsMap) {
                Object extraHeaders = paramsMap.get("extra_headers");
                if (extraHeaders instanceof Map<?, ?> map) {
                    Map<String, String> result = new LinkedHashMap<>();
                    map.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
                    return result;
                }
            }
        }
        return Map.of();
    }

    private static String findHeaderValue(Map<String, List<String>> headers, String key) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                List<String> values = entry.getValue();
                return values == null || values.isEmpty() ? "" : values.getFirst();
            }
        }
        return "";
    }

    private static void writeText(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private enum ModelBuildMode {
        MODEL,
        INIT_MODEL,
        REACT_CONFIG
    }

    private static final class CapturedRequest {
        private final AtomicReference<Map<String, List<String>>> headers = new AtomicReference<>(Map.of());
        private final AtomicReference<String> body = new AtomicReference<>("");

        private void capture(HttpExchange exchange) throws IOException {
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((key, values) -> copiedHeaders.put(key, List.copyOf(values)));
            headers.set(copiedHeaders);
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }

        private CapturedSnapshot snapshot() {
            return new CapturedSnapshot(headers.get(), body.get());
        }
    }

    private record CapturedSnapshot(Map<String, List<String>> headers, String body) {
        private String authorization() {
            return findHeaderValue(headers, "Authorization");
        }

        private String token() {
            return findHeaderValue(headers, "Token");
        }

        private String userId() {
            return findHeaderValue(headers, "UserID");
        }
    }

    private record SentParams(Map<String, String> extraHeaders, CapturedSnapshot captured) {
    }

    private static final class RecordingTracer implements Consumer<Map<String, Object>> {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public void accept(Map<String, Object> payload) {
            records.add(payload);
        }

        private List<Map<String, Object>> records() {
            return records;
        }
    }
}
