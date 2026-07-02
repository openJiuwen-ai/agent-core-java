/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API embedding model tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/embedding/test_openai_embedding.py}.</p>
 *
 * <p>Mirrors Python's {@code OpenAIEmbedding} in
 * {@code openjiuwen/core/retrieval/embedding/openai_embedding.py}.</p>
 */
class OpenAIEmbeddingTest {

    @Test
    void initWithApiKey() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());

        assertEquals("test-model", model.modelName);
        assertEquals("test-api-key", model.apiKey);
        assertEquals("https://api.example.com/v1", model.apiUrl);
    }

    @Test
    void initWithoutApiKey() {
        EmbeddingConfig noKey = EmbeddingConfig.builder()
                .modelName("test-model")
                .baseUrl("https://api.example.com/v1/embeddings")
                .build();

        assertThrows(IllegalArgumentException.class, () -> new StubOpenAIEmbedding(noKey));
    }

    @Test
    void initWithExtraHeaders() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(
                config(),
                60,
                3,
                Map.of("X-Custom-Header", "custom-value"),
                8);

        assertEquals("custom-value", model.headers.get("X-Custom-Header"));
    }

    @Test
    void initWithCustomParams() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config(), 120, 5, null, 16);

        assertEquals(120, model.timeout);
        assertEquals(5, model.maxRetries);
        assertEquals(16, model.maxBatchSize);
    }

    @Test
    void embedQuerySuccessEmbeddingFormat() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"embedding\":" + numericArray(384, 0.1d) + "}");

        List<Double> embedding = model.embedQuerySync("test query");

        assertEquals(384, embedding.size());
        assertTrue(embedding.stream().allMatch(Double.class::isInstance));
    }

    @Test
    void embedQuerySuccessEmbeddingBase64() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":\"" + base64Array(384, 0.1f) + "\"}]}");

        List<Double> embedding = model.embedQuerySync("test query", Map.of("encoding_format", "base64"));

        assertEquals(384, embedding.size());
        assertEquals(0.1d, embedding.get(0), 0.001d);
        assertTrue(embedding.stream().allMatch(Double.class::isInstance));
    }

    @Test
    void embedQuerySuccessEmbeddingsFormat() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"embeddings\":[" + numericArray(384, 0.1d) + "]}");

        List<Double> embedding = model.embedQuerySync("test query");

        assertEquals(384, embedding.size());
    }

    @Test
    void embedQuerySuccessDataFormat() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(384, 0.1d) + "}]}");

        List<Double> embedding = model.embedQuerySync("test query");

        assertEquals(384, embedding.size());
    }

    @Test
    void embedQueryEmptyText() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("   "));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("Empty text provided"));
    }

    @Test
    void embedQueryRetryOnFailure() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config(), 60, 3, null, 8);
        model.enqueueFailure(new IOException("Connection error"));
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(384, 0.1d) + "}]}");

        List<Double> embedding = model.embedQuerySync("test query");

        assertEquals(384, embedding.size());
        assertEquals(2, model.requestAttempts.get());
    }

    @Test
    void embedQueryMaxRetriesExceeded() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config(), 60, 2, null, 8);
        model.enqueueFailure(new IOException("Connection error"));
        model.enqueueFailure(new IOException("Connection error"));

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("test query"));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED, error.getStatus());
        assertEquals(2, model.requestAttempts.get());
    }

    @Test
    void embedQueryInvalidResponseFormat() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"data\":{\"invalid\":\"format\"}}");

        BaseError error = assertThrows(BaseError.class, () -> model.embedQuerySync("test query"));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("No embeddings in response"));
    }

    @Test
    void embedDocumentsSuccess() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"data\":["
                + "{\"index\":0,\"embedding\":" + numericArray(384, 0.1d) + "},"
                + "{\"index\":1,\"embedding\":" + numericArray(384, 0.2d) + "},"
                + "{\"index\":2,\"embedding\":" + numericArray(384, 0.3d) + "}"
                + "]}");

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2", "text 3"));

        assertEquals(3, embeddings.size());
        assertTrue(embeddings.stream().allMatch(embedding -> embedding.size() == 384));
    }

    @Test
    void embedDocumentsWithBatching() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config(), 60, 3, null, 1);
        for (int i = 0; i < 4; i++) {
            model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(384, 0.1d) + "}]}");
        }

        List<List<Double>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2", "text 3", "text 4"), 1, Map.of());

        assertEquals(4, model.requestAttempts.get());
        assertEquals(4, embeddings.size());
    }

    @Test
    void embedDocumentsRespectsMaxBatchSize() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config(), 60, 3, null, 2);
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(384, 0.3d) + "}]}");
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(384, 0.4d) + "}]}");

        model.embedDocumentsSync(List.of("text 1", "text 2", "text 3"), 5, Map.of());

        assertEquals(2, model.batchSizes.size());
        assertTrue(model.batchSizes.containsAll(List.of(2, 1)));
        assertEquals(2, model.requestAttempts.get());
    }

    @Test
    void embedDocumentsEmptyList() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());

        BaseError error = assertThrows(BaseError.class, () -> model.embedDocumentsSync(List.of()));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("Empty texts list provided"));
    }

    @Test
    void embedDocumentsWithEmptyTexts() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());

        BaseError error = assertThrows(BaseError.class,
                () -> model.embedDocumentsSync(List.of("text 1", "   ", "text 2")));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("chunks are empty"));
    }

    @Test
    void embedDocumentsAllEmpty() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());

        BaseError error = assertThrows(BaseError.class,
                () -> model.embedDocumentsSync(List.of("   ", "  ", "")));

        assertEquals(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, error.getStatus());
    }

    @Test
    void dimensionFromResponse() {
        StubOpenAIEmbedding model = new StubOpenAIEmbedding(config());
        model.enqueueResponse("{\"data\":[{\"index\":0,\"embedding\":" + numericArray(768, 0.1d) + "}]}");

        model.embedQuerySync("test");

        assertEquals(768, model.getDimension());
    }

    private static EmbeddingConfig config() {
        return EmbeddingConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .baseUrl("https://api.example.com/v1/embeddings")
                .build();
    }

    private static String numericArray(int size, double value) {
        StringBuilder builder = new StringBuilder(size * 4);
        builder.append('[');
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        builder.append(']');
        return builder.toString();
    }

    private static String base64Array(int size, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(size * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < size; i++) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static final class StubOpenAIEmbedding extends OpenAIEmbedding {

        private final List<Object> responses = new CopyOnWriteArrayList<>();
        private final AtomicInteger requestAttempts = new AtomicInteger();
        private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();

        private StubOpenAIEmbedding(EmbeddingConfig config) {
            this(config, 60, 3, null, 8);
        }

        private StubOpenAIEmbedding(
                EmbeddingConfig config,
                int timeout,
                int maxRetries,
                Map<String, String> extraHeaders,
                int maxBatchSize) {
            super(config, timeout, maxRetries, extraHeaders, maxBatchSize, 50, null, null);
        }

        private void enqueueResponse(String body) {
            responses.add(new ApiResponse(200, body));
        }

        private void enqueueFailure(IOException exception) {
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
            Object next = responses.remove(0);
            if (next instanceof IOException ioException) {
                throw ioException;
            }
            return (ApiResponse) next;
        }
    }
}
