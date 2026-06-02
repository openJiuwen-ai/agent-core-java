/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.embedding.VLLMEmbedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VLLM embedding model test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/embedding/test_vllm_embedding.py}.</p>
 */
class TestVLLMEmbedding {

    @Test
    void testParseMultimodalInputDefaultInstruction() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        Map<String, Object> kwargs = new LinkedHashMap<>();

        Map<String, Object> result = VLLMEmbedding.parseMultimodalInput(document, kwargs);
        List<Map<String, Object>> messages = extractMessages(result);

        assertSame(kwargs, result);
        assertFalse(result.containsKey("instruction"));
        assertEquals(List.of(
                Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", "Represent the user's input."))),
                Map.of("role", "user", "content", document.getContent())
        ), messages);
    }

    @Test
    void testParseMultimodalInputCustomInstruction() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("instruction", "Custom instruction text");

        Map<String, Object> result = VLLMEmbedding.parseMultimodalInput(document, kwargs);
        List<Map<String, Object>> messages = extractMessages(result);

        assertSame(kwargs, result);
        assertFalse(result.containsKey("instruction"));
        assertEquals(List.of(
                Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", "Custom instruction text"))),
                Map.of("role", "user", "content", document.getContent())
        ), messages);
    }

    @Test
    void testParseMultimodalInputNoneInstruction() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("instruction", null);

        Map<String, Object> result = VLLMEmbedding.parseMultimodalInput(document, kwargs);
        List<Map<String, Object>> messages = extractMessages(result);

        assertSame(kwargs, result);
        assertFalse(result.containsKey("instruction"));
        assertEquals(List.of(Map.of("role", "user", "content", document.getContent())), messages);
    }

    @Test
    void testParseMultimodalInputPreservesOtherKwargs() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("other_param", "value");
        kwargs.put("another", 123);

        VLLMEmbedding.parseMultimodalInput(document, kwargs);

        assertEquals("value", kwargs.get("other_param"));
        assertEquals(123, kwargs.get("another"));
        assertTrue(kwargs.containsKey("extra_body"));
    }

    @Test
    void testParseMultimodalInputWithMultimodalContent() {
        MultimodalDocument document = new MultimodalDocument()
                .addField("text", "Description")
                .addField("image", "data:image/png;base64,AAAA")
                .addField("audio", "data:audio/wav;base64,BBBB");
        Map<String, Object> kwargs = new LinkedHashMap<>();

        VLLMEmbedding.parseMultimodalInput(document, kwargs);
        List<Map<String, Object>> messages = extractMessages(kwargs);
        Map<String, Object> userMessage = messages.get(1);

        assertEquals(2, messages.size());
        assertEquals("user", userMessage.get("role"));
        assertEquals(3, ((List<?>) userMessage.get("content")).size());
    }

    @Test
    void testEmbedMultimodalSuccess() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        List<Float> embedding = model.embedMultimodal(document, new LinkedHashMap<>());

        assertEquals(384, embedding.size());
        assertTrue(embedding.stream().allMatch(value -> value instanceof Float));
        assertEquals(1, model.getEmbeddingsCallCount);
        assertEquals(2, extractMessages(model.lastCallOptions).size());
        assertEquals(document.getContent(), extractMessages(model.lastCallOptions).get(1).get("content"));
    }

    @Test
    void testEmbedMultimodalInvalidInput() {
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        BaseError error = assertThrows(BaseError.class, () -> model.embedMultimodal("not a document", new LinkedHashMap<>()));

        assertTrue(error.getMessage().contains("input provided for multimodal embedding is not a MultimodalDocument"));
    }

    @Test
    void testEmbedMultimodalWithInstruction() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        model.embedMultimodal(document, new LinkedHashMap<>(Map.of("instruction", "Custom instruction")));

        List<Map<String, Object>> messages = extractMessages(model.lastCallOptions);
        assertEquals("Custom instruction", castContent(messages.get(0).get("content")).get(0).get("text"));
    }

    @Test
    void testEmbedMultimodalSyncSuccess() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        List<Float> embedding = model.embedMultimodalSync(document);

        assertEquals(384, embedding.size());
        assertTrue(embedding.stream().allMatch(value -> value instanceof Float));
        assertEquals(1, model.getEmbeddingsCallCount);
        assertEquals(2, extractMessages(model.lastCallOptions).size());
        assertEquals(document.getContent(), extractMessages(model.lastCallOptions).get(1).get("content"));
    }

    @Test
    void testEmbedMultimodalSyncInvalidInput() {
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        BaseError error = assertThrows(BaseError.class, () -> model.embedMultimodalSync("not a document", new LinkedHashMap<>()));

        assertTrue(error.getMessage().contains("input provided for multimodal embedding is not a MultimodalDocument"));
    }

    @Test
    void testEmbedMultimodalSyncWithInstruction() {
        MultimodalDocument document = new MultimodalDocument().addField("text", "Hello world");
        StubVllmEmbedding model = new StubVllmEmbedding(createConfig());

        model.embedMultimodalSync(document, new LinkedHashMap<>(Map.of("instruction", "Custom instruction")));

        List<Map<String, Object>> messages = extractMessages(model.lastCallOptions);
        assertEquals("Custom instruction", castContent(messages.get(0).get("content")).get(0).get("text"));
    }

    private static EmbeddingConfig createConfig() {
        return new EmbeddingConfig(
                "test-model",
                "https://api.example.com/v1/embeddings",
                "test-api-key"
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractMessages(Map<String, Object> kwargs) {
        Map<String, Object> extraBody = kwargs.containsKey("extra_body")
                ? (Map<String, Object>) kwargs.get("extra_body")
                : kwargs;
        return castMessages(extraBody.get("messages"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMessages(Object value) {
        assertInstanceOf(List.class, value);
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castContent(Object value) {
        assertInstanceOf(List.class, value);
        return (List<Map<String, Object>>) value;
    }

    private static final class StubVllmEmbedding extends VLLMEmbedding {

        private Map<String, Object> lastCallOptions = Map.of();
        private int getEmbeddingsCallCount = 0;

        private StubVllmEmbedding(EmbeddingConfig config) {
            super(config);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options) {
            getEmbeddingsCallCount++;
            this.lastCallOptions = new LinkedHashMap<>(options);
            return List.of(createEmbeddingVector());
        }
    }

    private static List<Float> createEmbeddingVector() {
        List<Float> embedding = new ArrayList<>();
        for (int i = 0; i < 384; i++) {
            embedding.add(0.1f);
        }
        return embedding;
    }
}
