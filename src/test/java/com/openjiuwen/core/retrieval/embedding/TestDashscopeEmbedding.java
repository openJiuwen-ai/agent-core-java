/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DashscopeEmbedding.
 *
 * <p>Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_dashscope_embedding.py.</p>
 */
class TestDashscopeEmbedding {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmbeddingConfig embeddingConfig;
    private EmbeddingConfig embeddingConfigNoKey;

    @BeforeEach
    void setUp() {
        embeddingConfig = new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-api-key");
        embeddingConfigNoKey = new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/");
    }

    @Nested
    @DisplayName("DashscopeEmbedding initialization tests")
    class TestDashscopeEmbeddingInit {

        @Test
        @DisplayName("test init with api key")
        void testInitWithApiKey() {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            assertEquals("test-model", model.modelName);
            assertEquals("test-api-key", model.apiKey);
            assertEquals("https://dashscope.aliyuncs.com/api/v1/", model.apiUrl);
        }

        @Test
        @DisplayName("test init without api key")
        void testInitWithoutApiKey() {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfigNoKey);

            assertNull(model.apiKey);
        }

        @Test
        @DisplayName("test init with custom params")
        void testInitWithCustomParams() {
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 120, 5, null, 16, 25, null, mock(HttpClient.class));

            assertEquals(120, model.timeout);
            assertEquals(5, model.maxRetries);
            assertEquals(16, model.maxBatchSize);
            assertEquals(25, model.maxConcurrent);
        }

        @Test
        @DisplayName("test init with dimension matryoshka")
        void testInitWithDimensionMatryoshka() {
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 60, 3, null, 8, 50, 256, mock(HttpClient.class));

            assertTrue(model.isMatryoshkaDimension());
            assertEquals(256, model.getDimension());
            assertEquals(256, model.getRequestParams().get("dimension"));
        }

        @Test
        @DisplayName("test init without dimension")
        void testInitWithoutDimension() {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            assertFalse(model.isMatryoshkaDimension());
            assertFalse(model.getRequestParams().containsKey("dimension"));
        }
    }

    @Nested
    @DisplayName("DashscopeEmbedding response tests")
    class TestDashscopeEmbeddingHandleResponse {

        @Test
        @DisplayName("test handle response success")
        void testHandleResponseSuccess() throws Exception {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            List<List<Float>> result = model.parseDashscopeEmbeddings(MAPPER.readTree(
                    "{\"output\":{\"embeddings\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}}"));

            assertEquals(List.of(List.of(0.1f, 0.2f)), result);
        }

        @Test
        @DisplayName("test handle response sorts by index")
        void testHandleResponseSortsByIndex() throws Exception {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            List<List<Float>> result = model.parseDashscopeEmbeddings(MAPPER.readTree(
                    "{\"output\":{\"embeddings\":["
                            + "{\"index\":1,\"embedding\":[1.0]},"
                            + "{\"index\":0,\"embedding\":[0.0]}]}}"));

            assertEquals(List.of(List.of(0.0f), List.of(1.0f)), result);
        }

        @Test
        @DisplayName("test handle response sets dimension when none")
        void testHandleResponseSetsDimensionWhenNone() throws Exception {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            model.parseDashscopeEmbeddings(MAPPER.readTree(
                    "{\"output\":{\"embeddings\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}}"));

            assertEquals(3, model.getDimension());
        }

        @Test
        @DisplayName("test handle response empty embeddings raises")
        void testHandleResponseEmptyEmbeddingsRaises() throws Exception {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.parseDashscopeEmbeddings(MAPPER.readTree(
                    "{\"output\":{\"embeddings\":[]}}")));
        }

        @Test
        @DisplayName("test handle response no embeddings key raises")
        void testHandleResponseNoEmbeddingsKeyRaises() throws Exception {
            DashscopeEmbedding model = new DashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.parseDashscopeEmbeddings(MAPPER.readTree(
                    "{\"output\":{\"other\":[]}}")));
        }

        @Test
        @DisplayName("test handle response non 200 on last attempt raises")
        void testHandleResponseNon200OnLastAttemptRaises() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(500);
            when(response.body()).thenReturn("{\"message\":\"fail\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 60, 2, null, 8, 50, null, httpClient);

            assertThrows(BaseError.class, () -> model.embedDocuments(List.of("text"), 1, null));
            verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("test handle response non 200 not last attempt falls through to output")
        void testHandleResponseNon200NotLastAttemptFallsThroughToOutput() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> failed = mock(HttpResponse.class);
            HttpResponse<String> ok = mock(HttpResponse.class);
            when(failed.statusCode()).thenReturn(429);
            when(failed.body()).thenReturn("{}");
            when(ok.statusCode()).thenReturn(200);
            when(ok.body()).thenReturn("{\"output\":{\"embeddings\":[{\"index\":0,\"embedding\":[0.1]}]}}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(failed)
                    .thenReturn(ok);
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 60, 2, null, 8, 50, null, httpClient);

            assertEquals(List.of(List.of(0.1f)), model.embedDocuments(List.of("text"), 1, null));
        }
    }

    @Nested
    @DisplayName("DashscopeEmbedding multimodal tests")
    class TestDashscopeEmbeddingMultimodal {

        @Test
        @DisplayName("test embed multimodal success")
        void testEmbedMultimodalSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);
            MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

            List<Float> embedding = model.embedMultimodal(doc, null);

            assertEquals(List.of(0.1f, 0.2f), embedding);
            assertEquals(List.of(doc.getDashscopeInput()), model.lastInput);
        }

        @Test
        @DisplayName("test embed multimodal invalid input")
        void testEmbedMultimodalInvalidInput() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.embedMultimodal("not a document", null));
        }

        @Test
        @DisplayName("test embed multimodal sync success")
        void testEmbedMultimodalSyncSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);
            MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

            List<Float> embedding = model.embedMultimodalSync(doc, Map.of("purpose", "test"));

            assertEquals(List.of(0.1f, 0.2f), embedding);
            assertEquals("test", model.lastOptions.get("purpose"));
        }

        @Test
        @DisplayName("test embed multimodal sync invalid input")
        void testEmbedMultimodalSyncInvalidInput() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.embedMultimodalSync("not a document", null));
        }
    }

    @Nested
    @DisplayName("DashscopeEmbedding documents tests")
    class TestDashscopeEmbeddingDocuments {

        @Test
        @DisplayName("test embed documents success")
        void testEmbedDocumentsSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            List<List<Float>> embeddings = model.embedDocuments(List.of("text 1", "text 2"), 2, null);

            assertEquals(2, embeddings.size());
            assertEquals(List.of("text 1", "text 2"), model.lastInput);
        }

        @Test
        @DisplayName("test embed documents with multimodal docs")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void testEmbedDocumentsWithMultimodalDocs() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);
            MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

            List<List<Float>> embeddings = model.embedDocuments((List) List.of(doc), 1, null);

            assertEquals(1, embeddings.size());
            assertEquals(List.of(doc), model.lastInput);
        }

        @Test
        @DisplayName("test embed documents mixed str and multimodal docs")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void testEmbedDocumentsMixedStrAndMultimodalDocs() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);
            MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

            model.embedDocuments((List) List.of("plain text", doc), 2, null);

            assertEquals("plain text", model.lastInput.get(0));
            assertEquals(doc, model.lastInput.get(1));
        }

        @Test
        @DisplayName("test embed documents respects max batch size")
        void testEmbedDocumentsRespectsMaxBatchSize() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig, 2);

            model.embedDocuments(List.of("a", "b", "c"), 10, null);

            assertEquals(List.of(2, 1), model.batchSizes);
        }

        @Test
        @DisplayName("test embed documents sync success")
        void testEmbedDocumentsSyncSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            List<List<Float>> embeddings = model.embedDocumentsSync(List.of("text 1", "text 2"), 2, null);

            assertEquals(2, embeddings.size());
        }

        @Test
        @DisplayName("test embed documents sync mixed str and multimodal docs")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void testEmbedDocumentsSyncMixedStrAndMultimodalDocs() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);
            MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

            model.embedDocumentsSync((List) List.of("plain text", doc), 2, null);

            assertEquals("plain text", model.lastInput.get(0));
            assertEquals(doc, model.lastInput.get(1));
        }

        @Test
        @DisplayName("test embed documents empty list raises")
        void testEmbedDocumentsEmptyListRaises() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.embedDocuments(List.of(), null, null));
        }

        @Test
        @DisplayName("test embed documents sync empty list raises")
        void testEmbedDocumentsSyncEmptyListRaises() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            assertThrows(BaseError.class, () -> model.embedDocumentsSync(List.of(), null, null));
        }
    }

    @Nested
    @DisplayName("DashscopeEmbedding get embeddings tests")
    class TestDashscopeEmbeddingGetEmbeddings {

        @Test
        @DisplayName("test get embeddings async success")
        void testGetEmbeddingsAsyncSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            CompletableFuture<List<Float>> future = model.embedQueryAsync("query", null);

            assertEquals(List.of(0.1f, 0.2f), future.join());
        }

        @Test
        @DisplayName("test get embeddings sync success")
        void testGetEmbeddingsSyncSuccess() {
            StubDashscopeEmbedding model = new StubDashscopeEmbedding(embeddingConfig);

            assertEquals(List.of(0.1f, 0.2f), model.embedQuery("query", null));
        }

        @Test
        @DisplayName("test get embeddings async invalid response raises")
        void testGetEmbeddingsAsyncInvalidResponseRaises() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"output\":{\"embeddings\":[]}}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 60, 1, null, 8, 50, null, httpClient);

            assertThrows(BaseError.class, () -> model.embedQuery("query", null));
        }

        @Test
        @DisplayName("test get embeddings sync io failure raises")
        void testGetEmbeddingsSyncIoFailureRaises() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("boom"));
            DashscopeEmbedding model = new DashscopeEmbedding(
                    embeddingConfig, 60, 1, null, 8, 50, null, httpClient);

            assertThrows(BaseError.class, () -> model.embedQuery("query", null));
        }
    }

    private static final class StubDashscopeEmbedding extends DashscopeEmbedding {
        private List<?> lastInput = List.of();
        private Map<String, Object> lastOptions = Map.of();
        private final List<Integer> batchSizes = new ArrayList<>();

        private StubDashscopeEmbedding(EmbeddingConfig config) {
            this(config, 8);
        }

        private StubDashscopeEmbedding(EmbeddingConfig config, int maxBatchSize) {
            super(config, 60, 3, null, maxBatchSize, 50, null, mock(HttpClient.class));
        }

        @Override
        protected List<List<Float>> getEmbeddingsSync(List<?> texts, Map<String, Object> options) {
            lastInput = new ArrayList<>(texts);
            lastOptions = options == null ? Map.of() : Map.copyOf(options);
            batchSizes.add(texts.size());
            List<List<Float>> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(List.of(0.1f, 0.2f));
            }
            return embeddings;
        }
    }
}
