/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestChatReranker} in
 * {@code tests/unit_tests/core/retrieval/reranker/test_chat_reranker.py}.
 */
class ChatRerankerPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void testInitWithValidYesNoIds() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThat(model.requestParams("query", List.of("doc"), Boolean.TRUE).get("logit_bias"))
                .isEqualTo(Map.of(123, 5, 456, 5));
    }

    @Test
    void testInitWithoutYesNoIds() {
        RerankerConfig config = baseConfig().yesNoIds(null).build();

        assertThatThrownBy(() -> new ChatReranker(config))
                .hasMessageContaining("chat reranker require \"yes_no_ids\"");
    }

    @Test
    void testInitWithInvalidYesNoIds() {
        RerankerConfig config = baseConfig().yesNoIds(List.of(123)).build();

        assertThatThrownBy(() -> new ChatReranker(config))
                .hasMessageContaining("chat reranker require \"yes_no_ids\"");
    }

    @Test
    void testInitWithNonIntegerYesNoIds() {
        String json = """
                {
                  "model_name": "test-model",
                  "api_key": "test-api-key",
                  "api_base": "https://api.example.com/v1",
                  "yes_no_ids": ["yes", "no"]
                }
                """;

        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(json, RerankerConfig.class))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testRequestParamsWithInstructTrue() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Object> params = model.requestParams("test query", List.of("doc1"), Boolean.TRUE);

        assertThat(params).containsEntry("model", "test-model")
                .containsEntry("temperature", 0)
                .containsEntry("max_tokens", 1)
                .containsEntry("logprobs", true)
                .containsEntry("top_logprobs", 5)
                .containsEntry("logit_bias", Map.of(123, 5, 456, 5));
        assertMessagesContain(params, "test query", "<Document>: doc1");
    }

    @Test
    void testRequestParamsWithInstructFalse() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Object> params = model.requestParams("test query", List.of("doc1"), Boolean.FALSE);

        assertMessagesContain(params, "test query");
    }

    @Test
    void testRequestParamsWithCustomInstruct() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Object> params = model.requestParams("test query", List.of("doc1"), "Custom instruction");

        assertMessagesContain(params, "Custom instruction", "test query");
    }

    @Test
    void testRequestParamsWithExtraBody() {
        RerankerConfig config = baseConfig()
                .yesNoIds(List.of(123, 456))
                .extraBody(Map.of("custom_param", "custom_value"))
                .build();
        ChatReranker model = new ChatReranker(config);

        Map<String, Object> params = model.requestParams("test query", List.of("doc1"), Boolean.TRUE);

        assertThat(params).containsEntry("custom_param", "custom_value");
    }

    @Test
    void testParseResponseWithYesToken() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Double> result = model.parseResponse(
                response(token("yes", Math.log(0.8d)), token("no", Math.log(0.2d))),
                List.<Object>of("doc1")
        );

        assertThat(result).containsKey("doc1");
        assertThat(result.get("doc1")).isGreaterThan(0.5d);
    }

    @Test
    void testParseResponseWithNoToken() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Double> result = model.parseResponse(
                response(token("no", Math.log(0.8d)), token("yes", Math.log(0.2d))),
                List.<Object>of("doc1")
        );

        assertThat(result).containsKey("doc1");
        assertThat(result.get("doc1")).isLessThan(0.5d);
    }

    @Test
    void testParseResponseWithCaseInsensitiveTokens() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Double> result = model.parseResponse(
                response(token("YES", Math.log(0.8d)), token("No", Math.log(0.2d))),
                List.<Object>of("doc1")
        );

        assertThat(result.get("doc1")).isGreaterThan(0.5d);
    }

    @Test
    void testParseResponseWithTokenPrefix() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Double> result = model.parseResponse(
                response(token("yes,", Math.log(0.8d)), token("no.", Math.log(0.2d))),
                List.<Object>of("doc1")
        );

        assertThat(result).containsKey("doc1");
        assertThat(result.get("doc1")).isGreaterThan(0.5d);
    }

    @Test
    void testParseResponseWithoutLogprobs() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThatThrownBy(() -> model.parseResponse(Map.of("choices", List.of(Map.of())), List.<Object>of("doc1")))
                .hasMessageContaining("the service does not support logprobs");
    }

    @Test
    void testParseResponseWithEmptyLogprobs() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThatThrownBy(() -> model.parseResponse(
                Map.of("choices", List.of(Map.of("logprobs", Map.of()))),
                List.<Object>of("doc1")
        ))
                .hasMessageContaining("the service does not support logprobs");
    }

    @Test
    void testParseResponseWithZeroTotalProb() {
        ChatReranker model = new ChatReranker(chatConfig());

        Map<String, Double> result = model.parseResponse(
                response(token("maybe", -1000d)),
                List.<Object>of("doc1")
        );

        assertThat(result).containsEntry("doc1", 0.0d);
    }

    @Test
    void testParseResponseWithDocumentObject() {
        ChatReranker model = new ChatReranker(chatConfig());
        Document document = Document.builder().id("doc1").text("Test document").build();

        Map<String, Double> result = model.parseResponse(
                response(token("yes", Math.log(0.8d))),
                List.<Object>of(document)
        );

        assertThat(result).containsKey("doc1");
    }

    @Test
    void testAssembleParamsWithStringDoc() {
        ChatReranker model = new ChatReranker(chatConfig());

        ChatReranker.AssembleResult result =
                model.assembleParams("test query", List.<Object>of("doc1"), Boolean.TRUE, Map.of());

        assertThat(result.headers()).containsKey("Content-Type");
        assertThat(result.params()).containsEntry("model", "test-model");
        assertMessagesContain(result.params(), "doc1");
    }

    @Test
    void testAssembleParamsWithDocumentObject() {
        ChatReranker model = new ChatReranker(chatConfig());
        Document document = Document.builder().id("doc1").text("Test document").build();

        ChatReranker.AssembleResult result =
                model.assembleParams("test query", List.<Object>of(document), Boolean.TRUE, Map.of());

        assertMessagesContain(result.params(), "Test document");
    }

    @Test
    void testAssembleParamsInvalidInputNotList() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThatThrownBy(() -> model.assembleParams("test query", "not a list", Boolean.TRUE, Map.of()))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
    }

    @Test
    void testAssembleParamsInvalidInputWrongSize() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThatThrownBy(() -> model.assembleParams(
                "test query",
                List.<Object>of("doc1", "doc2"),
                Boolean.TRUE,
                Map.of()
        ))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
    }

    @Test
    void testAssembleParamsInvalidInputEmptyList() {
        ChatReranker model = new ChatReranker(chatConfig());

        assertThatThrownBy(() -> model.assembleParams("test query", List.of(), Boolean.TRUE, Map.of()))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
    }

    @Test
    void testRerankSuccess() {
        FakeHttpClient client = new FakeHttpClient(200, responseJson(token("yes", Math.log(0.8d)),
                token("no", Math.log(0.2d))));
        ChatReranker model = new ChatReranker(chatConfig(), 1, 0.1d, null, client);

        Map<String, Double> result = model.rerank("test query", List.<Object>of("doc1"), Boolean.TRUE, Map.of()).join();

        assertThat(result).containsKey("doc1");
        assertThat(result.get("doc1")).isGreaterThan(0.5d);
    }

    @Test
    void testRerankSyncSuccess() {
        FakeHttpClient client = new FakeHttpClient(200, responseJson(token("yes", Math.log(0.8d)),
                token("no", Math.log(0.2d))));
        ChatReranker model = new ChatReranker(chatConfig(), 1, 0.1d, null, client);

        Map<String, Double> result = model.rerankSync("test query", List.<Object>of("doc1"), Boolean.TRUE, Map.of());

        assertThat(result).containsKey("doc1");
        assertThat(result.get("doc1")).isGreaterThan(0.5d);
    }

    @Test
    void testTestCompatibilitySuccess() {
        FakeHttpClient client = new FakeHttpClient(200, responseJson(token("yes", Math.log(0.8d))));
        ChatReranker model = new ChatReranker(chatConfig(), 1, 0.1d, null, client);

        assertThat(model.testCompatibility()).isTrue();
    }

    @Test
    void testTestCompatibilityFailure() {
        FakeHttpClient client = new FakeHttpClient(new IOException("Service error"));
        ChatReranker model = new ChatReranker(chatConfig(), 1, 0.1d, null, client);

        assertThat(model.testCompatibility()).isFalse();
    }

    private static RerankerConfig chatConfig() {
        return baseConfig().yesNoIds(List.of(123, 456)).build();
    }

    private static RerankerConfig.RerankerConfigBuilder baseConfig() {
        return RerankerConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .apiBase("https://api.example.com/v1");
    }

    @SuppressWarnings("unchecked")
    private static void assertMessagesContain(Map<String, Object> params, String... fragments) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) params.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "system");
        assertThat(messages.get(1)).containsEntry("role", "user");
        String userContent = String.valueOf(messages.get(1).get("content"));
        assertThat(userContent).contains(fragments);
    }

    @SafeVarargs
    private static Map<String, Object> response(Map<String, Object>... topLogprobs) {
        return Map.of("choices", List.of(Map.of(
                "logprobs", Map.of(
                        "content", List.of(Map.of("top_logprobs", List.of(topLogprobs)))
                )
        )));
    }

    private static Map<String, Object> token(String token, double logprob) {
        return Map.of("token", token, "logprob", logprob);
    }

    @SafeVarargs
    private static String responseJson(Map<String, Object>... topLogprobs) {
        try {
            return OBJECT_MAPPER.writeValueAsString(response(topLogprobs));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private final IOException failure;

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
            this.failure = null;
        }

        private FakeHttpClient(IOException failure) {
            this.statusCode = 500;
            this.body = "";
            this.failure = failure;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            if (failure != null) {
                throw failure;
            }
            return (HttpResponse<T>) new FakeHttpResponse(request, statusCode, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.failedFuture(new IllegalStateException("sendAsync is not used"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record FakeHttpResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (key, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
