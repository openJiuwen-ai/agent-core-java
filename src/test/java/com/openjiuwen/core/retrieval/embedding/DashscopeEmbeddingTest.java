/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemImage;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingOutput;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResultItem;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.utils.JsonUtils;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DashscopeEmbeddingTest {
    @Test
    void initKeepsDashscopeFieldsAndDimension() {
        EmbeddingConfig config =
            new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-api-key");

        DashscopeEmbedding model =
            new DashscopeEmbedding(config, 120, 5, null, 16, 25, 256, mock(MultiModalEmbedding.class));

        assertEquals("test-model", model.modelName);
        assertEquals("test-api-key", model.apiKey);
        assertEquals("https://dashscope.aliyuncs.com/api/v1/", model.apiUrl);
        assertEquals(120, model.timeout);
        assertEquals(5, model.maxRetries);
        assertEquals(16, model.maxBatchSize);
        assertTrue(model.isMatryoshkaDimension());
        assertEquals(256, model.getDimension());
        assertEquals(256, model.getRequestParams().get("dimension"));
    }

    @Test
    void initWithoutDimensionDoesNotSetMatryoshkaFlag() {
        DashscopeEmbedding model =
            new DashscopeEmbedding(new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/"), 60, 3,
                    null, 8, 50, null, mock(MultiModalEmbedding.class));

        assertFalse(model.isMatryoshkaDimension());
        assertFalse(model.getRequestParams().containsKey("dimension"));
    }

    @Test
    void parseResponseSortsEmbeddingsByIndex() throws Exception {
        DashscopeEmbedding model = new DashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        List<List<Float>> embeddings =
            model.parseEmbeddings(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree("""
                    {"output":{"embeddings":[
                      {"index":1,"embedding":[0.2,0.3]},
                      {"index":0,"embedding":[0.1,0.4]}
                    ]}}
                    """));

        assertEquals(List.of(0.1f, 0.4f), embeddings.get(0));
        assertEquals(List.of(0.2f, 0.3f), embeddings.get(1));
    }

    @Test
    void parseResponseRejectsEmptyOrMissingEmbeddings() throws Exception {
        DashscopeEmbedding model = new DashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        assertThrows(BaseError.class, () -> model.parseEmbeddings(mapper.readTree("{\"output\":{\"embeddings\":[]}}")));
        assertThrows(BaseError.class,
                () -> model.parseEmbeddings(mapper.readTree("{\"output\":{\"other\":\"value\"}}")));
    }

    @Test
    void embedMultimodalConvertsDocumentToDashscopeInput() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");
        StubDashscopeEmbedding model = new StubDashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        List<Float> embedding = model.embedMultimodalSync(doc);

        assertEquals(List.of(0.1f, 0.2f), embedding);
        assertEquals(List.of(doc.getDashscopeInput()), model.lastInput);
    }

    @Test
    void embedQueryStringUsesDashscopeSdkPath() {
        StubDashscopeEmbedding model = new StubDashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        List<Float> embedding = model.embedQuery("plain text");

        assertEquals(List.of(0.1f, 0.2f), embedding);
        assertEquals(List.of("plain text"), model.lastInput);
    }

    @Test
    void getEmbeddingsCallsOfficialDashscopeSdkAndRetriesApiException() throws Exception {
        MultiModalEmbedding client = mock(MultiModalEmbedding.class);
        when(client.call(any(MultiModalEmbeddingParam.class))).thenThrow(new ApiException(new RuntimeException("boom")))
                .thenReturn(result(List.of(item(0, List.of(0.1, 0.2)))));
        DashscopeEmbedding model = new DashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"), 60, 2, null, 8,
                50, 256, client);

        List<List<Float>> embeddings = model.getDashscopeEmbeddings(List.of("hello"), Map.of());

        assertEquals(List.of(0.1f, 0.2f), embeddings.get(0));
        verify(client, times(2)).call(any(MultiModalEmbeddingParam.class));
    }

    @Test
    void embedMultimodalRejectsInvalidInput() {
        DashscopeEmbedding model = new DashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        assertThrows(BaseError.class, () -> model.embedMultimodal("not a document", Map.of()));
        assertThrows(BaseError.class, () -> model.embedMultimodalSync("not a document", Map.of()));
    }

    @Test
    void embedDocumentsConvertsMixedInputsAndRespectsBatchSize() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello");
        StubDashscopeEmbedding model = new StubDashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        List<List<Float>> embeddings = model.embedDocuments(List.of("plain text", doc, "another string"), 2, Map.of());

        assertEquals(3, embeddings.size());
        assertEquals(2, model.calls.size());
        // Batches run via supplyAsync; completion order is not guaranteed across platforms.
        List<List<Object>> expectedBatches = List.of(
                List.of("plain text", doc.getDashscopeInput()),
                List.of("another string"));
        assertTrue(model.calls.containsAll(expectedBatches) && expectedBatches.containsAll(model.calls),
                () -> "unexpected batches: " + model.calls);
    }

    @Test
    void embedDocumentsRejectsEmptyList() {
        DashscopeEmbedding model = new DashscopeEmbedding(
                new EmbeddingConfig("test-model", "https://dashscope.aliyuncs.com/api/v1/", "test-key"));

        assertThrows(BaseError.class, () -> model.embedDocuments(List.of(), null, Map.of()));
    }

    @Test
    void apiEmbeddingRetriesHttpNon2xxBeforeFailing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> serverError = mock(HttpResponse.class);
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(serverError.statusCode()).thenReturn(500);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"embeddings\":[[0.1,0.2]]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(serverError)
                .thenReturn(ok);
        APIEmbedding model =
            new APIEmbedding(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"), 60,
                    2, null, 8, 50, httpClient);

        List<Float> embedding = model.embedQuery("test");

        assertEquals(List.of(0.1f, 0.2f), embedding);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void dashscopeDocumentInputMatchesPythonShape() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello")
                .addField("image", "https://openjiuwen.com/img/jiuwen_logo.png")
                .addField("image", "data:image/png;base64,AA==");

        Map<String, Object> input = doc.getDashscopeInput();

        assertEquals("Hello", input.get("text"));
        assertFalse(input.containsKey("image"));
        assertEquals(List.of("https://openjiuwen.com/img/jiuwen_logo.png", "data:image/png;base64,AA=="),
                input.get("multi_images"));
    }

    @Test
    void toDashscopeItemsUsesOfficialSdkItemTypes() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello")
                .addField("image", "https://openjiuwen.com/img/jiuwen_logo.png")
                .addField("image", "data:image/png;base64,AA==");

        List<MultiModalEmbeddingItemBase> items = DashscopeEmbedding.toDashscopeItems(List.of(doc.getDashscopeInput()));

        assertEquals(3, items.size());
        assertTrue(items.get(0) instanceof MultiModalEmbeddingItemText);
        assertTrue(items.get(1) instanceof MultiModalEmbeddingItemImage);
        assertTrue(items.get(2) instanceof MultiModalEmbeddingItemImage);
    }

    @Test
    void dashscopeDocumentInputRejectsUnsupportedAudioAndBase64Video() {
        assertThrows(BaseError.class,
                () -> new MultimodalDocument().addField("audio", "data:audio/wav;base64,AA==").getDashscopeInput());
        assertThrows(BaseError.class,
                () -> new MultimodalDocument().addField("video", "data:video/mp4;base64,AA==").getDashscopeInput());
    }

    @Test
    void dashscopeDocumentInputRejectsDuplicateNonImageFields() {
        assertThrows(BaseError.class,
                () -> new MultimodalDocument().addField("text", "one").addField("text", "two").getDashscopeInput());
    }

    private static final class StubDashscopeEmbedding extends DashscopeEmbedding {
        private Object lastInput;
        private final List<List<Object>> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

        private StubDashscopeEmbedding(EmbeddingConfig config) {
            super(config);
        }

        @Override
        protected List<List<Float>> getDashscopeEmbeddings(Object input, Map<String, Object> options) {
            this.lastInput = input;
            @SuppressWarnings("unchecked")
            List<Object> batch = input instanceof List<?> list ? (List<Object>) list : List.of(input);
            calls.add(new LinkedHashMap<Integer, Object>() {
                {
                    for (int i = 0; i < batch.size(); i++) {
                        put(i, batch.get(i));
                    }
                }
            }.values().stream().toList());
            return batch.stream().map(item -> List.of(0.1f, 0.2f)).toList();
        }
    }

    private static MultiModalEmbeddingResult result(List<MultiModalEmbeddingResultItem> items) {
        MultiModalEmbeddingOutput output = new MultiModalEmbeddingOutput();
        output.setEmbeddings(items);
        DashScopeResult result = new DashScopeResult();
        result.setStatusCode(200);
        result.setOutput(JsonUtils.toJsonObject(output));
        return MultiModalEmbeddingResult.fromDashScopeResult(result);
    }

    private static MultiModalEmbeddingResultItem item(int index, List<Double> embedding) {
        MultiModalEmbeddingResultItem item = new MultiModalEmbeddingResultItem();
        item.setIndex(index);
        item.setEmbedding(embedding);
        return item;
    }
}
