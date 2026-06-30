/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.TqdmCallback;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
