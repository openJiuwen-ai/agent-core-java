/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code TestStandardReranker} in
 * {@code tests/unit_tests/core/retrieval/reranker/test_standard_reranker.py}.</p>
 */
class StandardRerankerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initWithApiKey() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());

        assertThat(field(reranker, "modelName")).isEqualTo("test-model");
        assertThat(field(reranker, "apiKey")).isEqualTo("test-api-key");
        assertThat(field(reranker, "apiUrl")).isEqualTo("https://api.example.com/v1");
    }

    @Test
    void initWithoutApiKey() {
        StandardReranker reranker = new StandardReranker(rerankerConfigNoKey());

        assertThat(field(reranker, "apiKey")).isEqualTo("");
    }

    @Test
    void initWithExtraHeaders() {
        StandardReranker reranker = new StandardReranker(
                rerankerConfig(),
                3,
                0.1d,
                Map.of("X-Custom-Header", "custom-value"),
                null
        );

        assertThat(headers(reranker)).containsEntry("X-Custom-Header", "custom-value");
    }

    @Test
    void initWithCustomParams() {
        RerankerConfig config = rerankerConfig();
        config.setTimeout(120.0d);
        StandardReranker reranker = new StandardReranker(config, 5, 0.5d, null, null);

        assertThat(field(reranker, "timeout")).isEqualTo(120.0d);
        assertThat(field(reranker, "maxRetries")).isEqualTo(5);
    }

    @Test
    void initApiUrlWithTrailingSlash() {
        RerankerConfig config = RerankerConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .apiBase("https://api.example.com/v1/")
                .build();

        assertThat(field(new StandardReranker(config), "apiUrl")).isEqualTo("https://api.example.com/v1");
    }

    @Test
    void initApiUrlWithEndpoint() {
        RerankerConfig config = RerankerConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .apiBase("https://api.example.com/v1/rerank")
                .build();

        assertThat(field(new StandardReranker(config), "apiUrl")).isEqualTo("https://api.example.com/v1");
    }

    @Test
    void requestHeaders() {
        Map<String, String> headers = headers(new StandardReranker(rerankerConfig()));

        assertThat(headers)
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Authorization", "Bearer test-api-key");
    }

    @Test
    void requestHeadersNoApiKey() {
        Map<String, String> headers = headers(new StandardReranker(rerankerConfigNoKey()));

        assertThat(headers).containsEntry("Content-Type", "application/json");
        assertThat(headers).doesNotContainKey("Authorization");
    }

    @Test
    void requestParamsWithInstructTrue() {
        Map<String, Object> params = requestParams(
                new StandardReranker(rerankerConfig()),
                "test query",
                List.of("doc1", "doc2"),
                true,
                Map.of()
        );

        assertThat(params)
                .containsEntry("model", "test-model")
                .containsEntry("return_documents", false);
        assertThat(params.get("query")).asString()
                .contains("<Instruct>")
                .contains("<Query>")
                .contains("test query");
    }

    @Test
    void requestParamsWithInstructFalse() {
        Map<String, Object> params = requestParams(
                new StandardReranker(rerankerConfig()),
                "test query",
                List.of("doc1", "doc2"),
                false,
                Map.of()
        );

        assertThat(params).containsEntry("model", "test-model").containsEntry("query", "test query");
    }

    @Test
    void requestParamsWithCustomInstruct() {
        Map<String, Object> params = requestParams(
                new StandardReranker(rerankerConfig()),
                "test query",
                List.of("doc1", "doc2"),
                "Custom instruction",
                Map.of()
        );

        assertThat(params.get("query")).asString().contains("Custom instruction").contains("test query");
    }

    @Test
    void requestParamsWithExtraBody() {
        RerankerConfig config = rerankerConfig();
        config.setExtraBody(Map.of("custom_param", "custom_value"));

        Map<String, Object> params = requestParams(
                new StandardReranker(config),
                "test query",
                List.of("doc1"),
                true,
                Map.of()
        );

        assertThat(params).containsEntry("custom_param", "custom_value");
    }

    @Test
    void parseResponseWithStringDocs() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());

        Map<String, Double> result = reranker.parseResponse(responseData(), List.of("doc1", "doc2"));

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.7d);
    }

    @Test
    void parseResponseWithDocumentObjects() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());
        List<Object> docs = List.of(new Document("doc1", "First document"), new Document("doc2", "Second document"));

        Map<String, Double> result = reranker.parseResponse(responseData(), docs);

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.7d);
    }

    @Test
    void parseResponseWithoutOutputKey() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());
        Map<String, Object> response = Map.of("results", results());

        Map<String, Double> result = reranker.parseResponse(response, List.of("doc1", "doc2"));

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.7d);
    }

    @Test
    void parseResponseMissingIndex() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());
        Map<String, Object> response = Map.of(
                "output",
                Map.of("results", List.of(
                        Map.of("index", 0, "relevance_score", 0.9d),
                        Map.of("relevance_score", 0.7d)
                ))
        );

        assertThatThrownBy(() -> reranker.parseResponse(response, List.of("doc1", "doc2")))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("missing index");
    }

    @Test
    void assembleParamsWithStringDocs() {
        Payload payload = assembleParams(new StandardReranker(rerankerConfig()), "test query", List.of("doc1", "doc2"));

        assertThat(payload.headers()).containsKey("Content-Type");
        assertThat(payload.params())
                .containsEntry("model", "test-model")
                .containsEntry("documents", List.of("doc1", "doc2"))
                .containsEntry("top_n", 2);
    }

    @Test
    void assembleParamsWithDocumentObjects() {
        Payload payload = assembleParams(
                new StandardReranker(rerankerConfig()),
                "test query",
                List.of(new Document("doc1", "First document"), new Document("doc2", "Second document"))
        );

        assertThat(payload.params()).containsEntry("documents", List.of("First document", "Second document"));
    }

    @Test
    void assembleParamsInvalidInput() {
        StandardReranker reranker = new StandardReranker(rerankerConfig());

        assertThatThrownBy(() -> assembleParams(reranker, "test query", List.of(123, 456)))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("input to reranker must be either list");
    }

    @Test
    void assembleParamsWithMultimodalDocument() {
        MultimodalDocument document = new MultimodalDocument();
        document.addField("text", "Hello world");
        document.setText("Hello world");

        Payload payload = assembleParams(new StandardReranker(rerankerConfig()), "test query", List.of(document));

        assertThat(payload.params()).containsEntry("documents", List.of("Hello world"));
    }

    @Test
    void rerankSuccess() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d, 0.7d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerank("test query", List.of("doc1", "doc2"), true, Map.of()).join();

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.7d);
    }

    @Test
    void rerankWithDocuments() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerank(
                "test query",
                List.of(new Document("doc1", "Test document")),
                true,
                Map.of()
        ).join();

        assertThat(result).containsEntry("doc1", 0.9d);
    }

    @Test
    void rerankWithInstructFalse() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerank("test query", List.of("doc1"), false, Map.of()).join();

        assertThat(result).containsEntry("doc1", 0.9d);
        assertThat(httpClient.lastJson()).containsEntry("query", "test query");
    }

    @Test
    void rerankWithCustomInstruct() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerank(
                "test query",
                List.of("doc1"),
                "Custom instruction",
                Map.of()
        ).join();

        assertThat(result).containsEntry("doc1", 0.9d);
        assertThat(httpClient.lastJson().get("query")).asString().contains("Custom instruction");
    }

    @Test
    void rerankSyncSuccess() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d, 0.7d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerankSync("test query", List.of("doc1", "doc2"), true, Map.of());

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.7d);
    }

    @Test
    void rerankSyncWithDocuments() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerankSync(
                "test query",
                List.of(new Document("doc1", "Test document")),
                true,
                Map.of()
        );

        assertThat(result).containsEntry("doc1", 0.9d);
    }

    @Test
    void rerankSyncWithInstructFalse() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerankSync("test query", List.of("doc1"), false, Map.of());

        assertThat(result).containsEntry("doc1", 0.9d);
        assertThat(httpClient.lastJson()).containsEntry("query", "test query");
    }

    @Test
    void rerankSyncWithCustomInstruct() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse(0.9d));
        StandardReranker reranker = new StandardReranker(rerankerConfig(), 3, 0.1d, null, httpClient);

        Map<String, Double> result = reranker.rerankSync(
                "test query",
                List.of("doc1"),
                "Custom instruction",
                Map.of()
        );

        assertThat(result).containsEntry("doc1", 0.9d);
        assertThat(httpClient.lastJson().get("query")).asString().contains("Custom instruction");
    }

    private static RerankerConfig rerankerConfig() {
        return RerankerConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .apiBase("https://api.example.com/v1")
                .build();
    }

    private static RerankerConfig rerankerConfigNoKey() {
        return RerankerConfig.builder()
                .modelName("test-model")
                .apiBase("https://api.example.com/v1")
                .build();
    }

    private static Map<String, Object> responseData() {
        return Map.of("output", Map.of("results", results()));
    }

    private static List<Map<String, Object>> results() {
        return List.of(
                Map.of("index", 0, "relevance_score", 0.9d),
                Map.of("index", 1, "relevance_score", 0.7d)
        );
    }

    private static String successResponse(double... scores) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (int index = 0; index < scores.length; index++) {
            results.add(Map.of("index", index, "relevance_score", scores[index]));
        }
        try {
            return MAPPER.writeValueAsString(Map.of("output", Map.of("results", results)));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(StandardReranker reranker) {
        return (Map<String, String>) field(reranker, "headers");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestParams(
            StandardReranker reranker,
            String query,
            List<String> documents,
            Object instruct,
            Map<String, Object> kwargs
    ) {
        try {
            Method method = StandardReranker.class.getDeclaredMethod(
                    "requestParams",
                    String.class,
                    List.class,
                    Object.class,
                    Map.class
            );
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(reranker, query, documents, instruct, kwargs);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Payload assembleParams(StandardReranker reranker, String query, List<Object> documents) {
        try {
            Method method = StandardReranker.class.getDeclaredMethod(
                    "assembleParams",
                    String.class,
                    List.class,
                    Object.class,
                    Map.class
            );
            method.setAccessible(true);
            Object payload = method.invoke(reranker, query, documents, true, Map.of());
            return new Payload(
                    (Map<String, String>) field(payload, "headers"),
                    (Map<String, Object>) field(payload, "params")
            );
        } catch (ReflectiveOperationException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(ex);
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Payload(Map<String, String> headers, Map<String, Object> params) {
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final String responseBody;
        private Map<String, Object> lastJson = new LinkedHashMap<>();

        private RecordingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        private Map<String, Object> lastJson() {
            return lastJson;
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
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            lastJson = MAPPER.readValue(bodyAsString(request), new TypeReference<>() {
            });
            return (HttpResponse<T>) new RecordingResponse(request, responseBody, 200);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            } catch (IOException ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private static String bodyAsString(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher()
                    .orElseThrow(() -> new IOException("request body missing"));
            BodySubscriber subscriber = new BodySubscriber();
            publisher.subscribe(subscriber);
            return subscriber.body().toCompletableFuture().join();
        }
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<String> body = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            bytes.writeBytes(chunk);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toString(StandardCharsets.UTF_8));
        }

        private CompletionStage<String> body() {
            return body;
        }
    }

    private record RecordingResponse(HttpRequest request, String body, int statusCode) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
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
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
