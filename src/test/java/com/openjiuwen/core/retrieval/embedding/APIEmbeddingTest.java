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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/retrieval/embedding/api_embedding.py}.
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
    void embedDocumentsRespectsMaxBatchSizeAndPreservesOrder() {
        StubAPIEmbedding model = new StubAPIEmbedding(config(), 60, 3, null, 2, 10);
        model.enqueueResponse("{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}");
        model.enqueueResponse("{\"embeddings\":[[0.5,0.6],[0.7,0.8]]}");
        model.enqueueResponse("{\"embeddings\":[[0.9,1.0]]}");

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("a", "b", "c", "d", "e"), 5, Map.of());

        assertEquals(List.of(2, 2, 1), model.batchSizes);
        assertEquals(5, embeddings.size());
        assertEquals(List.of(0.1d, 0.2d), embeddings.get(0));
        assertEquals(List.of(0.9d, 1.0d), embeddings.get(4));
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
