/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VLLMEmbeddingTest {

    @Test
    void parseMultimodalInputUsesDefaultInstruction() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

        Map<String, Object> kwargs = VLLMEmbedding.parseMultimodalInput(doc, new LinkedHashMap<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) ((Map<String, Object>) kwargs.get("extra_body")).get("messages");

        assertEquals("system", messages.getFirst().get("role"));
        assertEquals("user", messages.get(1).get("role"));
    }

    @Test
    void parseMultimodalInputHonorsExplicitNullInstruction() {
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("instruction", null);

        Map<String, Object> result = VLLMEmbedding.parseMultimodalInput(doc, kwargs);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) ((Map<String, Object>) result.get("extra_body")).get("messages");

        assertEquals(1, messages.size());
        assertEquals("user", messages.getFirst().get("role"));
        assertFalse(result.containsKey("instruction"));
    }

    @Test
    void embedMultimodalSyncBuildsExtraBodyMessages() {
        StubVllmEmbedding model = new StubVllmEmbedding(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));
        MultimodalDocument doc = new MultimodalDocument().addField("text", "Hello world");

        List<Float> embedding = model.embedMultimodalSync(doc, Map.of("instruction", "Custom instruction"));

        assertEquals(List.of(0.1f, 0.2f), embedding);
        assertTrue(model.lastOptions.containsKey("extra_body"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) ((Map<String, Object>) model.lastOptions.get("extra_body")).get("messages");
        assertEquals("Custom instruction", ((Map<?, ?>) ((List<?>) messages.getFirst().get("content")).getFirst()).get("text"));
    }

    @Test
    void embedMultimodalRejectsInvalidInput() {
        VLLMEmbedding model = new VLLMEmbedding(new EmbeddingConfig("test-model", "https://api.example.com/v1/embeddings", "test-key"));

        assertThrows(BaseError.class, () -> model.embedMultimodal("not a document", Map.of()));
        assertThrows(BaseError.class, () -> model.embedMultimodalSync("not a document", Map.of()));
    }

    private static final class StubVllmEmbedding extends VLLMEmbedding {

        private Map<String, Object> lastOptions = Map.of();

        private StubVllmEmbedding(EmbeddingConfig config) {
            super(config);
        }

        @Override
        protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options) {
            this.lastOptions = new LinkedHashMap<>(options);
            return List.of(List.of(0.1f, 0.2f));
        }
    }
}
