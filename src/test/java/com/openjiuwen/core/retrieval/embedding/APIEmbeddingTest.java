/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/retrieval/embedding/api_embedding.py}.
 * <p>
 * Mirrors Python's {@code TestAPIEmbedding} in
 * {@code tests/unit_tests/core/retrieval/embedding/test_api_embedding.py}.
 */
class APIEmbeddingTest {

    @Test
    void initKeepsApiFieldsAndHeaders() {
        APIEmbedding model = new StubAPIEmbedding(config(), 120, 5, Map.of("X-Test", "1"), 16, 10);

        assertEquals("test-model", model.modelName);
        assertEquals("test-api-key", model.apiKey);
        assertEquals("https://api.example.com/v1/embeddings", model.apiUrl);
        assertEquals(120, model.timeout);
        assertEquals(5, model.maxRetries);
        assertEquals(16, model.maxBatchSize);
        assertEquals("1", model.headers.get("X-Test"));
    }

    @Test
    void initWithApiKeyKeepsConfiguredFields() {
        APIEmbedding model = new StubAPIEmbedding(config());

        assertEquals("test-model", model.modelName);
        assertEquals("test-api-key", model.apiKey);
        assertEquals("https://api.example.com/v1/embeddings", model.apiUrl);
        assertEquals("Bearer test-api-key", model.headers.get("Authorization"));
    }

    @Test
    void initWithoutApiKeyLeavesAuthorizationHeaderUnset() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("test-model")
                .baseUrl("https://api.example.com/v1/embeddings")
                .build();

        APIEmbedding model = new StubAPIEmbedding(config);

        assertEquals(null, model.apiKey);
        assertFalse(model.headers.containsKey("Authorization"));
    }

    @Test
    void initWithExtraHeadersMergesCustomValues() {
        APIEmbedding model = new StubAPIEmbedding(config(), 60, 3, Map.of("X-Custom-Header", "custom-value"), 8, 50);

        assertEquals("application/json", model.headers.get("Content-Type"));
        assertEquals("custom-value", model.headers.get("X-Custom-Header"));
    }

    @Test
    void initWithCustomParamsAppliesTimeoutRetriesAndBatchSize() {
        APIEmbedding model = new StubAPIEmbedding(config(), 120, 5, null, 16, 25);

        assertEquals(120, model.timeout);
        assertEquals(5, model.maxRetries);
        assertEquals(16, model.maxBatchSize);
        assertEquals(25, model.getMaxConcurrent());
    }

    @Test
    void initSemaphoreUsesConfiguredMaxConcurrent() {
        APIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 8, 25);

        assertEquals(25, model.getLimiter().availablePermits());
    }

    @Test
    void initSemaphoreUsesDefaultMaxConcurrent() {
        APIEmbedding model = new StubAPIEmbedding(config());

        assertEquals(50, model.getLimiter().availablePermits());
    }

    @Test
    void executorIsCreatedWithConfiguredWorkersAndPrefix() throws Exception {
        APIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 8, 10);

        Future<String> threadName = model.executor.submit(() -> Thread.currentThread().getName());

        assertEquals(10, model.executor.getMaximumPoolSize());
        assertTrue(threadName.get().startsWith("openjiuwen_embed-"));
    }

    @Test
    void validateEmbedDocsRejectsEmptyAndBlankTexts() {
        APIEmbedding model = new StubAPIEmbedding(config());

        BaseError emptyList = assertThrows(
                BaseError.class,
                () -> model.embedDocumentsSync(List.of(), 1, Map.of())
        );
        BaseError blankChunk = assertThrows(
                BaseError.class,
                () -> model.embedDocumentsSync(List.of("text 1", "   ", "text 2"), 1, Map.of())
        );

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, emptyList.getStatus());
        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, blankChunk.getStatus());
    }

    @Test
    void embedQueryRejectsBlankText() {
        APIEmbedding model = new StubAPIEmbedding(config());

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("   "));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("Empty text provided"));
    }

    @Test
    void validateEmbedDocsRejectsInvalidCallbackClass() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> APIEmbedding.validateEmbedDocs(List.of("a"), Map.of("callback_cls", String.class))
        );

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID, error.getStatus());
    }

    @Test
    void embedQueryParsesEmbeddingAndCachesDimension() {
        StubAPIEmbedding model = new StubAPIEmbedding(config());
        model.enqueueResponse("{\"embedding\":[0.1,0.2,0.3]}");

        List<Double> embedding = model.embedQuerySync("test");

        assertEquals(List.of(0.1d, 0.2d, 0.3d), embedding);
        assertEquals(3, model.getDimension());
    }

    @Test
    void embedQueryParsesEmbeddingsAndDataFormats() {
        StubAPIEmbedding embeddingsFormat = new StubAPIEmbedding(config());
        embeddingsFormat.enqueueResponse("{\"embeddings\":[[0.1,0.2]]}");
        StubAPIEmbedding dataFormat = new StubAPIEmbedding(config());
        dataFormat.enqueueResponse("{\"data\":[{\"embedding\":[0.3,0.4]}]}");

        assertEquals(List.of(0.1d, 0.2d), embeddingsFormat.embedQuerySync("query"));
        assertEquals(List.of(0.3d, 0.4d), dataFormat.embedQuerySync("query"));
    }

    @Test
    void embedQueryRejectsInvalidResponseFormat() {
        StubAPIEmbedding model = new StubAPIEmbedding(config());
        model.enqueueResponse("{\"invalid\":\"format\"}");

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("query"));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("No embeddings in response"));
    }

    @Test
    void embedQueryRetriesThenSucceeds() {
        StubAPIEmbedding model = new StubAPIEmbedding(config());
        model.enqueueFailure(new IOException("boom"));
        model.enqueueResponse("{\"embedding\":[0.5,0.6]}");

        List<Double> embedding = model.embedQuerySync("query");

        assertEquals(List.of(0.5d, 0.6d), embedding);
        assertEquals(2, model.requestAttempts.get());
    }

    @Test
    void embedQueryFailsAfterMaxRetries() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 2, null, 8, 50);
        model.enqueueFailure(new IOException("network down"));
        model.enqueueFailure(new IOException("network down"));

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("query"));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED, error.getStatus());
        assertEquals(2, model.requestAttempts.get());
    }

    @Test
    void embedDocumentsSuccessParsesOneResponsePerDocument() {
        StubAPIEmbedding model = new StubAPIEmbedding(config());
        model.enqueueResponse("{\"embeddings\":[[0.1,0.2],[0.3,0.4],[0.5,0.6]]}");

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2", "text 3"));

        assertEquals(3, embeddings.size());
        assertEquals(List.of(0.1d, 0.2d), embeddings.get(0));
        assertEquals(List.of(0.5d, 0.6d), embeddings.get(2));
    }

    @Test
    void embedDocumentsRespectsMaxBatchSizeAndPreservesOrder() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 2, 10);
        model.enqueueResponseForInput(List.of("a", "b"), "{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}");
        model.enqueueResponseForInput(List.of("c", "d"), "{\"embeddings\":[[0.5,0.6],[0.7,0.8]]}");
        model.enqueueResponseForInput(List.of("e"), "{\"embeddings\":[[0.9,1.0]]}");

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("a", "b", "c", "d", "e"), 5, Map.of());

        List<Integer> sortedBatchSizes = new ArrayList<>(model.batchSizes);
        sortedBatchSizes.sort(Integer::compareTo);
        assertEquals(List.of(1, 2, 2), sortedBatchSizes);
        assertEquals(5, embeddings.size());
        assertEquals(List.of(0.1d, 0.2d), embeddings.get(0));
        assertEquals(List.of(0.9d, 1.0d), embeddings.get(4));
    }

    @Test
    void embedDocumentsWithBatchSizeOneCallsOncePerText() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 1, 10);
        model.enqueueResponse("{\"embeddings\":[[0.1,0.2]]}");
        model.enqueueResponse("{\"embeddings\":[[0.3,0.4]]}");
        model.enqueueResponse("{\"embeddings\":[[0.5,0.6]]}");
        model.enqueueResponse("{\"embeddings\":[[0.7,0.8]]}");

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2", "text 3", "text 4"), 1, Map.of());

        assertEquals(4, model.requestAttempts.get());
        assertEquals(4, embeddings.size());
    }

    @Test
    void embedDocumentsRejectsAllEmptyTexts() {
        APIEmbedding model = new StubAPIEmbedding(config());

        BaseError error = assertThrows(
                BaseError.class,
                () -> model.embedDocumentsSync(List.of("   ", "  ", ""), 1, Map.of())
        );

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
    }

    @Test
    void embedDocumentsUsesCallbackPerBatch() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 1, 10);
        model.enqueueResponse("{\"embeddings\":[[0.1,0.2]]}");
        model.enqueueResponse("{\"embeddings\":[[0.3,0.4]]}");
        model.enqueueResponse("{\"embeddings\":[[0.5,0.6]]}");
        model.enqueueResponse("{\"embeddings\":[[0.7,0.8]]}");
        CountingCallback callback = new CountingCallback(List.of(0, 1, 2, 3));

        List<List<Double>> embeddings = model.embedDocumentsSync(
                List.of("a", "b", "c", "d"),
                1,
                Map.of("callback", callback)
        );

        assertEquals(4, embeddings.size());
        assertEquals(4, callback.getCallCounter());
    }

    @Test
    void embedDocumentsHonorsConfiguredConcurrency() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 1, 2);
        model.sleepMillis = 20L;
        for (int i = 0; i < 4; i++) {
            model.enqueueResponse("{\"embeddings\":[[0.1,0.2]]}");
        }

        model.embedDocumentsSync(List.of("a", "b", "c", "d"), 1, Map.of());

        assertTrue(model.maxConcurrentSeen.get() <= 2);
        assertEquals(2, model.getMaxConcurrent());
    }

    @Test
    void cleanPayloadOptionsRemovesCallbackKeys() {
        Map<String, Object> cleaned = APIEmbedding.cleanPayloadOptions(Map.of(
                "callback", new CountingCallback(List.of(0)),
                "callback_cls", CountingCallback.class,
                "encoding_format", "float"
        ));

        assertEquals(Map.of("encoding_format", "float"), cleaned);
    }

    @Test
    void embedQueryAsyncReturnsCompletableFuture() {
        StubAPIEmbedding model = new StubAPIEmbedding(config());
        model.enqueueResponse("{\"embedding\":[0.1,0.2]}");

        Object future = model.embedQuery("query", Map.of());

        assertInstanceOf(java.util.concurrent.CompletableFuture.class, future);
        assertEquals(List.of(0.1d, 0.2d), ((java.util.concurrent.CompletableFuture<List<Double>>) future).join());
    }

    private static EmbeddingConfig config() {
        return EmbeddingConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .baseUrl("https://api.example.com/v1/embeddings")
                .build();
    }

    private static final class CountingCallback extends BaseCallback {

        CountingCallback(List<?> seq) {
            super(seq);
        }
    }

    private static class StubAPIEmbedding extends APIEmbedding {

        private final List<Object> responses = new CopyOnWriteArrayList<>();
        private final Map<List<String>, ApiResponse> responsesByInput = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger requestAttempts = new AtomicInteger();
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxConcurrentSeen = new AtomicInteger();
        private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        private volatile long sleepMillis;

        StubAPIEmbedding(EmbeddingConfig config) {
            this(config, 60, 3, null, 8, 50);
        }

        StubAPIEmbedding(EmbeddingConfig config,
                         int timeout,
                         int maxRetries,
                         Map<String, String> extraHeaders,
                         int maxBatchSize,
                         int maxConcurrent) {
            super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, HttpClientStubFactory.httpClient());
        }

        void enqueueResponse(String body) {
            responses.add(new ApiResponse(200, body));
        }

        void enqueueResponseForInput(List<String> input, String body) {
            responsesByInput.put(List.copyOf(input), new ApiResponse(200, body));
        }

        void enqueueFailure(Exception exception) {
            responses.add(exception);
        }

        @Override
        protected ApiResponse doRequest(Map<String, Object> payload) throws IOException {
            requestAttempts.incrementAndGet();
            Object input = payload.get("input");
            if (input instanceof List<?> list) {
                batchSizes.add(list.size());
            } else {
                batchSizes.add(1);
            }
            int current = activeRequests.incrementAndGet();
            maxConcurrentSeen.accumulateAndGet(current, Math::max);
            try {
                if (sleepMillis > 0) {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", exception);
                    }
                }
                if (input instanceof List<?> list) {
                    ApiResponse response = responsesByInput.get(List.copyOf(list));
                    if (response != null) {
                        return response;
                    }
                }
                Object next = responses.removeFirst();
                if (next instanceof IOException ioException) {
                    throw ioException;
                }
                if (next instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                return (ApiResponse) next;
            } finally {
                activeRequests.decrementAndGet();
            }
        }
    }

    private static final class HttpClientStubFactory {
        private HttpClientStubFactory() {
        }

        static java.net.http.HttpClient httpClient() {
            return java.net.http.HttpClient.newHttpClient();
        }
    }
}
