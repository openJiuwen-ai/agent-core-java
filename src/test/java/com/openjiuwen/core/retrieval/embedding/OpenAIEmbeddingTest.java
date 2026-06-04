/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpenAIEmbeddingTest {

    @Test
    public void testInitWithApiKey() {
        try (OpenAIEmbedding model = newModel(mock(HttpClient.class))) {
            assertAll(
                    () -> assertEquals("test-model", model.modelName),
                    () -> assertEquals("test-api-key", model.apiKey),
                    () -> assertEquals("https://api.example.com/v1", model.apiUrl));
        }
    }

    @Test
    public void testInitWithoutApiKey() {
        EmbeddingConfig config = new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new OpenAIEmbedding(config, 1, 3, null, 8, 50, null, mock(HttpClient.class), Map.of()));

        assertTrue(error.getMessage().contains("API key"));
    }

    @Test
    public void testInitWithExtraHeaders() {
        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                1,
                3,
                Map.of("X-Custom-Header", "custom-value"),
                8,
                50,
                null,
                mock(HttpClient.class))) {
            assertEquals("custom-value", model.headers.get("X-Custom-Header"));
        }
    }

    @Test
    public void testInitWithCustomParams() {
        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                120,
                5,
                null,
                16,
                50,
                null,
                mock(HttpClient.class))) {
            assertAll(
                    () -> assertEquals(120, model.timeout),
                    () -> assertEquals(5, model.maxRetries),
                    () -> assertEquals(16, model.maxBatchSize));
        }
    }

    @Test
    public void testEmbedQuerySuccessEmbeddingFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(384, "0.1")));

        try (OpenAIEmbedding model = newModel(httpClient)) {
            List<Float> embedding = model.embedQuery("test query");

            assertEquals(384, embedding.size());
            assertTrue(embedding.stream().allMatch(value -> value instanceof Float));
        }
    }

    @Test
    public void testEmbedQuerySuccessEmbeddingBase64() throws Exception {
        String encoded = base64Embedding(384, 0.1f);
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, "{\"data\":[{\"index\":0,\"embedding\":\"" + encoded + "\"}]}");

        try (OpenAIEmbedding model = newModel(httpClient)) {
            List<Float> embedding = model.embedQuery("test query", Map.of("encoding_format", "base64"));

            assertEquals(384, embedding.size());
            assertTrue(embedding.stream().allMatch(value -> value instanceof Float));
            for (Float value : embedding) {
                assertEquals(0.1f, value, 0.001f);
            }
        }
    }

    @Test
    public void testEmbedQuerySuccessEmbeddingsFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(384, "0.1")));

        try (OpenAIEmbedding model = newModel(httpClient)) {
            List<Float> embedding = model.embedQuery("test query");

            assertEquals(384, embedding.size());
        }
    }

    @Test
    public void testEmbedQuerySuccessDataFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(384, "0.1")));

        try (OpenAIEmbedding model = newModel(httpClient)) {
            List<Float> embedding = model.embedQuery("test query");

            assertEquals(384, embedding.size());
        }
    }

    @Test
    public void testEmbedQueryEmptyText() {
        try (OpenAIEmbedding model = newModel(mock(HttpClient.class))) {
            BaseError error = assertThrows(BaseError.class, () -> model.embedQuery("   "));

            assertTrue(error.getMessage().contains("Empty text provided"));
        }
    }

    @Test
    public void testEmbedQueryRetryOnFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = response(dataResponse(embeddingArray(384, "0.1")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection error"))
                .thenReturn(response);

        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                1,
                3,
                null,
                8,
                50,
                null,
                httpClient)) {
            List<Float> embedding = model.embedQuery("test query");

            assertEquals(384, embedding.size());
            verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    public void testEmbedQueryMaxRetriesExceeded() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection error"));

        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                1,
                2,
                null,
                8,
                50,
                null,
                httpClient)) {
            BaseError error = assertThrows(BaseError.class, () -> model.embedQuery("test query"));

            assertTrue(error.getMessage().contains("Connection error"));
            verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    public void testEmbedQueryInvalidResponseFormat() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, "{\"data\":{\"invalid\":\"format\"}}");

        try (OpenAIEmbedding model = newModel(httpClient)) {
            BaseError error = assertThrows(BaseError.class, () -> model.embedQuery("test query"));

            assertTrue(error.getMessage().contains("No embeddings in response"));
        }
    }

    @Test
    public void testEmbedDocumentsSuccess() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(
                embeddingArray(384, "0.1"),
                embeddingArray(384, "0.2"),
                embeddingArray(384, "0.3")));

        try (OpenAIEmbedding model = newModel(httpClient)) {
            List<List<Float>> embeddings = model.embedDocuments(List.of("text 1", "text 2", "text 3"), null);

            assertEquals(3, embeddings.size());
            assertTrue(embeddings.stream().allMatch(embedding -> embedding.size() == 384));
        }
    }

    @Test
    public void testEmbedDocumentsWithBatching() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(384, "0.1")));

        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                1,
                3,
                null,
                1,
                50,
                null,
                httpClient)) {
            List<List<Float>> embeddings = model.embedDocuments(List.of("text 1", "text 2", "text 3", "text 4"), 1);

            verify(httpClient, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            assertEquals(4, embeddings.size());
        }
    }

    @Test
    public void testEmbedDocumentsRespectsMaxBatchSize() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(384, "0.1")));

        try (OpenAIEmbedding model = new OpenAIEmbedding(
                embeddingConfig(),
                1,
                3,
                null,
                2,
                50,
                null,
                httpClient)) {
            model.embedDocuments(List.of("text 1", "text 2", "text 3"), 5);

            verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    public void testEmbedDocumentsEmptyList() {
        try (OpenAIEmbedding model = newModel(mock(HttpClient.class))) {
            BaseError error = assertThrows(BaseError.class, () -> model.embedDocuments(List.of(), null));

            assertTrue(error.getMessage().contains("Empty texts list provided"));
        }
    }

    @Test
    public void testEmbedDocumentsWithEmptyTexts() {
        try (OpenAIEmbedding model = newModel(mock(HttpClient.class))) {
            BaseError error = assertThrows(
                    BaseError.class,
                    () -> model.embedDocuments(List.of("text 1", "   ", "text 2"), null));

            assertTrue(error.getMessage().contains("chunks are empty"));
        }
    }

    @Test
    public void testEmbedDocumentsAllEmpty() {
        try (OpenAIEmbedding model = newModel(mock(HttpClient.class))) {
            assertThrows(BaseError.class, () -> model.embedDocuments(List.of("   ", "  ", ""), null));
        }
    }

    @Test
    public void testDimensionFromResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        stubResponse(httpClient, dataResponse(embeddingArray(768, "0.1")));

        try (OpenAIEmbedding model = newModel(httpClient)) {
            model.embedQuery("test");

            assertEquals(768, model.getDimension());
        }
    }

    private static EmbeddingConfig embeddingConfig() {
        return new EmbeddingConfig(
                "test-model",
                "https://api.example.com/v1/embeddings",
                "test-api-key");
    }

    private static OpenAIEmbedding newModel(HttpClient httpClient) {
        return new OpenAIEmbedding(embeddingConfig(), 1, 3, null, 8, 50, null, httpClient);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubResponse(HttpClient httpClient, String body) throws Exception {
        HttpResponse<String> response = response(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String dataResponse(String... embeddings) {
        StringJoiner joiner = new StringJoiner(",", "{\"data\":[", "]}");
        for (int i = 0; i < embeddings.length; i++) {
            joiner.add("{\"index\":" + i + ",\"embedding\":" + embeddings[i] + "}");
        }
        return joiner.toString();
    }

    private static String embeddingArray(int dimension, String value) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int i = 0; i < dimension; i++) {
            joiner.add(value);
        }
        return joiner.toString();
    }

    private static String base64Embedding(int dimension, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(dimension * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dimension; i++) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}
