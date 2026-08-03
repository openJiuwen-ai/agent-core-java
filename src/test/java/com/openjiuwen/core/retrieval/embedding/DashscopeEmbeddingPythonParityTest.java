/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/unit_tests/core/retrieval/embedding/test_dashscope_embedding.py}.
 */
class DashscopeEmbeddingPythonParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initWithApiKeyKeepsModelKeyAndUrl() {
        DashscopeEmbedding model = new DashscopeEmbedding(config());

        assertThat(model.modelName).isEqualTo("test-model");
        assertThat(model.apiKey).isEqualTo("test-api-key");
        assertThat(model.apiUrl).isEqualTo("https://dashscope.aliyuncs.com/api/v1/");
    }

    @Test
    void initWithoutApiKeyLeavesKeyNull() {
        DashscopeEmbedding model = new DashscopeEmbedding(configWithoutKey());

        assertThat(model.apiKey).isNull();
    }

    @Test
    void initWithCustomParamsKeepsTimeoutRetriesBatchAndConcurrency() {
        DashscopeEmbedding model = new DashscopeEmbedding(config(), 120, 5, null, 16, 25, null, null);

        assertThat(model.timeout).isEqualTo(120);
        assertThat(model.maxRetries).isEqualTo(5);
        assertThat(model.maxBatchSize).isEqualTo(16);
        assertThat(model.getMaxConcurrent()).isEqualTo(25);
    }

    @Test
    void initWithDimensionSetsMatryoshkaRequestParam() {
        DashscopeEmbedding model = new DashscopeEmbedding(config(), 60, 3, null, 8, 50, 256, null);

        assertThat(model.isMatryoshkaDimension()).isTrue();
        assertThat(model.getDimension()).isEqualTo(256);
        assertThat(model.getRequestParams()).containsEntry("dimension", 256);
    }

    @Test
    void initWithoutDimensionDoesNotSetRequestDimension() {
        DashscopeEmbedding model = new DashscopeEmbedding(config());

        assertThat(model.isMatryoshkaDimension()).isFalse();
        assertThat(model.getRequestParams()).doesNotContainKey("dimension");
    }

    @Test
    void handleResponseSuccessReturnsEmbeddings() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        List<List<Double>> result = model.handle(200, null, null, output("""
                {"embeddings":[
                  {"index":0,"embedding":[0.1,0.1,0.1]},
                  {"index":1,"embedding":[0.2,0.2,0.2]}
                ]}
                """), 0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(0.1d, 0.1d, 0.1d);
        assertThat(result.get(1)).containsExactly(0.2d, 0.2d, 0.2d);
    }

    @Test
    void handleResponseSortsByIndex() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        List<List<Double>> result = model.handle(200, null, null, output("""
                {"embeddings":[
                  {"index":1,"embedding":[0.2]},
                  {"index":0,"embedding":[0.1]}
                ]}
                """), 0);

        assertThat(result).containsExactly(List.of(0.1d), List.of(0.2d));
    }

    @Test
    void handleResponseSetsDimensionWhenNone() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        model.handle(200, null, null, output("""
                {"embeddings":[{"index":0,"embedding":[0.1,0.2,0.3,0.4]}]}
                """), 0);

        assertThat(model.getDimension()).isEqualTo(4);
    }

    @Test
    void handleResponseEmptyEmbeddingsRaises() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.handle(200, null, null, output("{\"embeddings\":[]}"), 0))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("The embeddings field in response is empty");
    }

    @Test
    void handleResponseWithoutEmbeddingsRaises() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.handle(200, null, null, output("{\"other_key\":\"value\"}"), 0))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("No embeddings in response");
    }

    @Test
    void handleResponseNon200OnLastAttemptRaisesRequestFailed() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config(), 2);

        assertThatThrownBy(() -> model.handle(500, "InternalError", "Server error", output("{}"), 1))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Failed to get embedding after 2 attempts");
    }

    @Test
    void handleResponseNon200BeforeLastAttemptStillParsesOutput() throws Exception {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config(), 3);

        assertThatThrownBy(() -> model.handle(500, "InternalError", "Server error", output("{}"), 0))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("No embeddings in response");
    }

    @Test
    void embedMultimodalSuccessUsesDashscopeInput() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(vectorResponse(384, 0.1d));

        List<Double> embedding = model.embedMultimodalSync(document, Map.of());

        assertThat(embedding).hasSize(384).allSatisfy(value -> assertThat(value).isInstanceOf(Double.class));
        assertThat(model.recordedBatches).containsExactly(List.of(document.getDashscopeInput()));
    }

    @Test
    void embedMultimodalInvalidInputRaises() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.embedMultimodalSync((Object) "not a document", Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("input provided for multimodal embedding is not a MultimodalDocument");
    }

    @Test
    void embedMultimodalAsyncSuccessUsesDashscopeInput() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(vectorResponse(384, 0.1d));

        List<Double> embedding = model.embedMultimodal(document, Map.of()).join();

        assertThat(embedding).hasSize(384);
        assertThat(model.recordedBatches).containsExactly(List.of(document.getDashscopeInput()));
    }

    @Test
    void embedMultimodalSyncInvalidInputRaises() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.embedMultimodalSync((Object) 123, Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("input provided for multimodal embedding is not a MultimodalDocument");
    }

    @Test
    void embedDocumentsSuccessReturnsAllEmbeddings() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(List.of(vector(384, 0.1d), vector(384, 0.2d), vector(384, 0.3d)));

        List<List<Double>> embeddings = model.embedDocuments(List.of("text 1", "text 2", "text 3"), null, Map.of())
                .join();

        assertThat(embeddings).hasSize(3).allSatisfy(embedding -> assertThat(embedding).hasSize(384));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void embedDocumentsConvertsMultimodalDocuments() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello");
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(vectorResponse(384, 0.1d));

        model.embedDocuments((List) List.of(document), null, Map.of()).join();

        assertThat(model.recordedBatches).containsExactly(List.of(document.getDashscopeInput()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void embedDocumentsKeepsMixedStringAndMultimodalOrder() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello");
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(List.of(vector(384, 0.1d), vector(384, 0.2d), vector(384, 0.3d)));

        model.embedDocuments((List) List.of("plain text", document, "another string"), null, Map.of()).join();

        assertThat(model.recordedBatches.get(0))
                .containsExactly("plain text", document.getDashscopeInput(), "another string");
    }

    @Test
    void embedDocumentsRespectsMaxBatchSize() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config(), 3, 2);
        model.enqueue(List.of(vector(384, 0.1d), vector(384, 0.2d)));
        model.enqueue(vectorResponse(384, 0.3d));

        List<List<Double>> embeddings = model.embedDocuments(List.of("a", "b", "c"), null, Map.of()).join();

        assertThat(embeddings).hasSize(3);
        assertThat(model.recordedBatches).containsExactly(List.of("a", "b"), List.of("c"));
    }

    @Test
    void embedDocumentsEmptyListRaises() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.embedDocuments(List.of(), null, Map.of()).join())
                .hasCauseInstanceOf(BaseError.class)
                .hasMessageContaining("Empty texts list provided");
    }

    @Test
    void embedDocumentsSyncSuccessReturnsAllEmbeddings() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(List.of(vector(384, 0.1d), vector(384, 0.2d)));

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2"), null, Map.of());

        assertThat(embeddings).hasSize(2).allSatisfy(embedding -> assertThat(embedding).hasSize(384));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void embedDocumentsSyncKeepsMixedStringAndMultimodalOrder() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello");
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());
        model.enqueue(List.of(vector(384, 0.1d), vector(384, 0.2d)));

        model.embedDocumentsSync((List) List.of("plain text", document), null, Map.of());

        assertThat(model.recordedBatches.get(0)).containsExactly("plain text", document.getDashscopeInput());
    }

    @Test
    void embedDocumentsSyncEmptyListRaises() {
        RecordingDashscopeEmbedding model = new RecordingDashscopeEmbedding(config());

        assertThatThrownBy(() -> model.embedDocumentsSync(List.of(), null, Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Empty texts list provided");
    }

    @Test
    void getEmbeddingsSyncReturnsEmbeddingsFromDashscopeShape() {
        FakeHttpClient client = new FakeHttpClient(200, """
                {"output":{"embeddings":[{"index":0,"embedding":[0.1,0.2,0.3]}]}}
                """);
        DashscopeEmbedding model = new DashscopeEmbedding(config(), 60, 3, null, 8, 50, null, client);

        List<List<Double>> result = model.getEmbeddingsSync(List.of("hello"), Map.of());

        assertThat(result).containsExactly(List.of(0.1d, 0.2d, 0.3d));
        assertThat(client.lastRequest.uri().toString())
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1//services/embeddings/text-embedding/text-embedding");
    }

    @Test
    void getEmbeddingsSyncFinalHttpFailureRaisesRequestFailed() {
        FakeHttpClient client = new FakeHttpClient(500, """
                {"code":"InternalError","message":"Server error","output":{}}
                """);
        DashscopeEmbedding model = new DashscopeEmbedding(config(), 60, 1, null, 8, 50, null, client);

        assertThatThrownBy(() -> model.getEmbeddingsSync(List.of("hello"), Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Failed to get embedding after 1 attempts: Server error");
    }

    @Test
    void getEmbeddingsAsyncInvalidResponseRaisesWithoutRetrying() {
        FakeHttpClient client = new FakeHttpClient(200, """
                {"output":{"embeddings":[]}}
                """);
        DashscopeEmbedding model = new DashscopeEmbedding(config(), 60, 3, null, 8, 50, null, client);

        assertThatThrownBy(() -> model.getEmbeddingsSync(List.of("hello"), Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("The embeddings field in response is empty");
        assertThat(client.callCount).isEqualTo(1);
    }

    private static JsonNode output(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    private static List<List<Double>> vectorResponse(int dimension, double value) {
        return List.of(vector(dimension, value));
    }

    private static List<Double> vector(int dimension, double value) {
        List<Double> result = new ArrayList<>();
        for (int index = 0; index < dimension; index++) {
            result.add(value);
        }
        return result;
    }

    private static EmbeddingConfig config() {
        return EmbeddingConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .baseUrl("https://dashscope.aliyuncs.com/api/v1/")
                .build();
    }

    private static EmbeddingConfig configWithoutKey() {
        return EmbeddingConfig.builder()
                .modelName("test-model")
                .baseUrl("https://dashscope.aliyuncs.com/api/v1/")
                .build();
    }

    private static final class RecordingDashscopeEmbedding extends DashscopeEmbedding {

        private final Queue<List<List<Double>>> queuedResponses = new ArrayDeque<>();
        private final List<List<Object>> recordedBatches = new ArrayList<>();

        private RecordingDashscopeEmbedding(EmbeddingConfig config) {
            this(config, 3, 8);
        }

        private RecordingDashscopeEmbedding(EmbeddingConfig config, int maxRetries) {
            this(config, maxRetries, 8);
        }

        private RecordingDashscopeEmbedding(EmbeddingConfig config, int maxRetries, int maxBatchSize) {
            super(config, 60, maxRetries, null, maxBatchSize, 50, null, null);
        }

        private void enqueue(List<List<Double>> response) {
            queuedResponses.add(response);
        }

        private List<List<Double>> handle(int statusCode,
                                          String errorCode,
                                          String errorMessage,
                                          JsonNode output,
                                          int attempt) {
            return handleDashscopeApiResponse(statusCode, errorCode, errorMessage, output, attempt);
        }

        @Override
        protected List<List<Double>> getEmbeddingsSync(List<?> texts, Map<String, Object> kwargs) {
            recordedBatches.add(new ArrayList<>(texts));
            return queuedResponses.remove();
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private int callCount;
        private HttpRequest lastRequest;

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
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
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.lastRequest = request;
            this.callCount++;
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
