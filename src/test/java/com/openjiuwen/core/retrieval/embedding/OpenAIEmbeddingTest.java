/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAIEmbeddingTest {

    @Test
    void initNormalizesEmbeddingsEndpoint() {
        OpenAIEmbedding model = new OpenAIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                null,
                mock(HttpClient.class));

        assertEquals("https://api.example.com/v1", model.apiUrl);
    }

    @Test
    void embedQueryParsesOpenAiListResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        OpenAIEmbedding model = new OpenAIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                null,
                httpClient);

        assertEquals(List.of(0.1f, 0.2f, 0.3f), model.embedQuery("test query"));
    }

    @Test
    void embedQueryParsesBase64Response() throws Exception {
        byte[] raw = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0.1f)
                .putFloat(0.2f)
                .array();
        String base64 = Base64.getEncoder().encodeToString(raw);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[{\"index\":0,\"embedding\":\"" + base64 + "\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        OpenAIEmbedding model = new OpenAIEmbedding(
                new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"),
                60,
                3,
                null,
                8,
                50,
                null,
                httpClient);

        assertEquals(List.of(0.1f, 0.2f), model.embedQuery("test query"));
    }

    @Test
    void embedDocumentsRejectsBlankEntries() {
        OpenAIEmbedding model = new OpenAIEmbedding(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedDocuments(List.of("text", "   "), 1));
    }
}
