/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.TqdmCallback;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_api_embedding.py.
 */
class APIEmbeddingTest {

    @Test
    void initKeepsApiFieldsAndHeaders() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key");
        APIEmbedding model = new APIEmbedding(config, 120, 5, Map.of("X-Test", "1"), 16, 10, mock(HttpClient.class));

        assertEquals("test-model", model.modelName);
        assertEquals("test-key", model.apiKey);
        assertEquals("https://api.example.com/v1/embeddings", model.apiUrl);
        assertEquals(120, model.timeout);
        assertEquals(5, model.maxRetries);
        assertEquals(16, model.maxBatchSize);
        assertEquals("1", model.headers.get("X-Test"));
    }

    @Test
    void initWithoutApiKeyLeavesAuthorizationUnset() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings");
        APIEmbedding model = new APIEmbedding(config);

        assertNull(model.apiKey);
        assertTrue(model.headers.containsKey("Content-Type"));
        assertFalse(model.headers.containsKey("Authorization"));
    }

    @Test
    void initDisablesSslVerificationWhenConfigured() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key");
        config.setVerifySsl(false);

        APIEmbedding model = new APIEmbedding(config);

        assertEquals("", model.httpClient.sslParameters().getEndpointIdentificationAlgorithm());
    }

    @Test
    void embedQueryParsesEmbeddingResponseAndCachesDimension() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"embedding\":[0.1,0.2,0.3]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                httpClient);

        List<Float> embedding = model.embedQuery("test query");

        assertEquals(List.of(0.1f, 0.2f, 0.3f), embedding);
        assertEquals(3, model.getDimension());
    }

    @Test
    void embedQueryRetriesOnFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"embeddings\":[[0.1,0.2]]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("boom"))
                .thenReturn(response);

        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                2,
                null,
                8,
                50,
                httpClient);

        List<Float> embedding = model.embedQuery("test query");

        assertEquals(List.of(0.1f, 0.2f), embedding);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void embedDocumentsRejectsEmptyOrBlankInputs() {
        APIEmbedding model = new APIEmbedding(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedDocuments(List.of(), 1));
        assertThrows(BaseError.class, () -> model.embedDocuments(List.of("text 1", "   ", "text 2"), 1));
    }

    @Test
    void embedDocumentsUsesCallbackPerBatch() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"embeddings\":[[0.1,0.2]]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                1,
                10,
                httpClient);

        TqdmCallback callback = new TqdmCallback(List.of(0, 1, 2, 3));
        List<List<Float>> embeddings = model.embedDocuments(
                List.of("a", "b", "c", "d"),
                1,
                Map.of("callback", callback));

        assertEquals(4, embeddings.size());
        assertEquals(4, callback.getCallCounter());
    }

    @Test
    void initSemaphoreUsesConfiguredConcurrencyLimit() {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                25,
                mock(HttpClient.class));

        assertEquals(25, model.maxConcurrent);
        assertNotNull(model.executor);
    }

    @Test
    void initSemaphoreDefaultUsesFiftyConcurrentRequests() {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertEquals(50, model.maxConcurrent);
    }

    @Test
    void executorResourceManagementUsesNamedDaemonThreads() throws Exception {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                1,
                mock(HttpClient.class));

        String threadName = model.executor.submit(() -> Thread.currentThread().getName()).get();
        model.close();

        assertTrue(threadName.startsWith("openjiuwen_embed"));
        assertTrue(model.executor.isShutdown());
    }

    @Test
    void embedDocumentsHonorsConcurrencyLimit() {
        StubAPIEmbedding model = new StubAPIEmbedding(8, 2);
        model.sleepMillis = 25;

        model.embedDocuments(List.of("a", "b", "c", "d"), 1);

        assertTrue(model.maxConcurrentSeen.get() <= 2);
    }

    @Test
    void embedQuerySuccessDataFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = okResponse("{\"data\":[{\"embedding\":[0.4,0.5]}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                httpClient);

        assertEquals(List.of(0.4f, 0.5f), model.embedQuery("query"));
    }

    @Test
    void embedQueryRejectsEmptyText() {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedQuery(""));
        assertThrows(BaseError.class, () -> model.embedQuery("   "));
    }

    @Test
    void embedQueryMaxRetriesExceededOnIOException() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                2,
                null,
                8,
                50,
                httpClient);

        assertThrows(BaseError.class, () -> model.embedQuery("query"));
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void embedQueryRetriesNonSuccessHttpStatusUntilExceeded() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"message\":\"fail\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                2,
                null,
                8,
                50,
                httpClient);

        assertThrows(BaseError.class, () -> model.embedQuery("query"));
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void embedQueryRejectsInvalidResponseFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = okResponse("{\"data\":[{\"text\":\"missing\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                httpClient);

        assertThrows(BaseError.class, () -> model.embedQuery("query"));
    }

    @Test
    void embedDocumentsSuccess() {
        StubAPIEmbedding model = new StubAPIEmbedding(8, 50);

        List<List<Float>> embeddings = model.embedDocuments(List.of("a", "b", "c"), 2);

        assertEquals(3, embeddings.size());
        assertEquals(List.of(0.1f, 0.2f), embeddings.getFirst());
    }

    @Test
    void embedDocumentsRespectsMaxBatchSize() {
        StubAPIEmbedding model = new StubAPIEmbedding(2, 1);

        model.embedDocuments(List.of("a", "b", "c", "d", "e"), 4);

        assertEquals(List.of(2, 2, 1), model.batchSizes);
    }

    @Test
    void embedDocumentsRejectsAllEmpty() {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedDocuments(List.of(" ", "\t"), 1));
    }

    @Test
    void validateDocumentsRejectsInvalidCallbackClass() {
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedDocuments(
                List.of("a"),
                1,
                Map.of("callback_cls", String.class)));
    }

    @Test
    void embedQueryParsesNestedEmbeddingField() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = okResponse("{\"embedding\":[[0.7,0.8]]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                httpClient);

        assertEquals(List.of(0.7f, 0.8f), model.embedQuery("query"));
    }

    @Test
    void embedQueryParsesEmbeddingsField() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = okResponse("{\"embeddings\":[[0.9,1.0]]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        APIEmbedding model = new APIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                httpClient);

        assertEquals(List.of(0.9f, 1.0f), model.embedQuery("query"));
    }

    @Test
    void cleanPayloadOptionsRemovesCallbackOptions() {
        Map<String, Object> cleaned = APIEmbedding.cleanPayloadOptions(Map.of(
                "callback", new TqdmCallback(List.of(0)),
                "callback_cls", TqdmCallback.class,
                "encoding_format", "float"));

        assertEquals(Map.of("encoding_format", "float"), cleaned);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> okResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static class StubAPIEmbedding extends APIEmbedding {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrentSeen = new AtomicInteger();
        private final List<Integer> batchSizes = new ArrayList<>();
        private long sleepMillis;

        StubAPIEmbedding(int maxBatchSize, int maxConcurrent) {
            super(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                    60, 3, null, maxBatchSize, maxConcurrent, mock(HttpClient.class));
        }

        @Override
        protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options) {
            int current = active.incrementAndGet();
            maxConcurrentSeen.accumulateAndGet(current, Math::max);
            try {
                if (sleepMillis > 0) {
                    Thread.sleep(sleepMillis);
                }
                List<?> values = input instanceof List<?> list ? list : List.of(input);
                batchSizes.add(values.size());
                List<List<Float>> embeddings = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    embeddings.add(List.of(0.1f, 0.2f));
                }
                return embeddings;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
