/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.extractor;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.TestModelClient;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.LLMTripleExtractor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Triple extractor test cases.
 *
 * <p>Mirrors Python's {@code test_triple_extractor.py} for the Java
 * {@link LLMTripleExtractor} implementation.</p>
 */
class TestTripleExtractor {

    @Test
    void testInit() throws Exception {
        BaseModelClient llmClient = new TestModelClient("gpt-4o", "{\"triples\":[]}");

        LLMTripleExtractor extractor = new LLMTripleExtractor(llmClient, "test-model", 0.0f, 10);

        assertEquals(llmClient, readField(extractor, "llmClient"));
        assertEquals("test-model", readField(extractor, "modelName"));
        assertEquals(0.0f, (Float) readField(extractor, "temperature"));
        assertEquals(10, readField(extractor, "maxConcurrent"));
    }

    @Test
    void testInitWithDefaults() throws Exception {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "{\"triples\":[]}"),
                "test-model");

        assertEquals(0.0f, (Float) readField(extractor, "temperature"));
        assertEquals(50, readField(extractor, "maxConcurrent"));
    }

    @Test
    void testExtractMultipleChunks() {
        CountingModelClient llmClient = new CountingModelClient(
                "{\"triples\":[[\"Alice\",\"knows\",\"Bob\"],[\"Bob\",\"works_at\",\"Company\"]]}");
        LLMTripleExtractor extractor = new LLMTripleExtractor(llmClient, "test-model", 0.0f, 2);

        List<Triple> triples = extractor.extract(List.of(
                chunk("1", "Alice knows Bob"),
                chunk("2", "Charlie knows David")), Map.of());

        assertEquals(4, triples.size());
        assertEquals(2, llmClient.callCount.get());
    }

    @Test
    void testExtractWithException() throws Exception {
        BaseModelClient llmClient = mock(BaseModelClient.class);
        doThrow(new RuntimeException("429 too many requests"))
                .when(llmClient)
                .invoke(any(), any(), anyFloat(), any(), anyString(), any(), any(), any(), any(), anyMap());

        LLMTripleExtractor extractor = new LLMTripleExtractor(llmClient, "test-model");

        BaseError error = assertThrows(BaseError.class,
                () -> extractor.extract(List.of(chunk("1", "Alice knows Bob")), Map.of()));

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("triple extraction failed"));
    }

    @Test
    void testExtractInvalidJson() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "Invalid JSON response"),
                "test-model");

        BaseError error = assertThrows(BaseError.class,
                () -> extractor.extract(List.of(chunk("1", "Alice knows Bob")), Map.of()));

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void testExtractEmptyChunks() {
        BaseModelClient llmClient = mock(BaseModelClient.class);
        LLMTripleExtractor extractor = new LLMTripleExtractor(llmClient, "test-model");

        List<Triple> triples = extractor.extract(List.of(), Map.of());

        assertTrue(triples.isEmpty());
        verifyNoInteractions(llmClient);
    }

    @Test
    void testParseTriplesJsonArrayResponse() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "[[\"a\",\"b\",\"c\"]]"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertEquals(1, triples.size());
        assertEquals("a", triples.getFirst().getSubject());
        assertEquals("b", triples.getFirst().getPredicate());
        assertEquals("c", triples.getFirst().getObject());
    }

    @Test
    void testParseTriplesExtraFieldsIgnored() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "[[\"a\",\"b\",\"c\",\"ignored\",99]]"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertEquals(1, triples.size());
        assertEquals("c", triples.getFirst().getObject());
        assertNull(triples.getFirst().getConfidence());
    }

    @Test
    void testParseTriplesWrappedDictResponse() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "{\"triples\":[[\"x\",\"y\",\"z\"]]}"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertEquals(1, triples.size());
        assertEquals("x", triples.getFirst().getSubject());
    }

    @Test
    void testParseTriplesPromptShapeResponse() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o",
                        "{\"named_entities\":[\"Alice\",\"Bob\"],\"triples\":[[\"Alice\",\"knows\",\"Bob\"]]}"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertEquals(1, triples.size());
        assertEquals("Alice", triples.getFirst().getSubject());
    }

    @Test
    void testParseTriplesMissingTriplesKeyFails() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "{\"named_entities\":[\"Alice\",\"Bob\"]}"),
                "test-model");

        BaseError error = assertThrows(BaseError.class,
                () -> extractor.extract(List.of(chunk("c1", "payload")), Map.of()));

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void testParseTriplesInvalidItemsIgnoredWhenAnyValidItemExists() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o",
                        "{\"triples\":[[\"a\",\"b\",\"c\"],[\"x\"],{\"bad\":1},[\"y\",[\"nested\"],\"z\"]]}"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertEquals(2, triples.size());
        assertEquals("a", triples.getFirst().getSubject());
    }

    @Test
    void testParseTriplesAllInvalidFails() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(
                new TestModelClient("gpt-4o", "{\"triples\":[[\"x\"],{\"bad\":1}]}"),
                "test-model");

        List<Triple> triples = extractor.extract(List.of(chunk("c1", "payload")), Map.of());

        assertTrue(triples.isEmpty());
    }

    private static TextChunk chunk(String id, String text) {
        return new TextChunk(id, text, "doc_1", Map.of("title", "Test"), null);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class CountingModelClient extends TestModelClient {
        private final AtomicInteger callCount = new AtomicInteger();

        private CountingModelClient(String responseText) {
            super("gpt-4o", responseText);
        }

        @Override
        public com.openjiuwen.core.foundation.llm.schema.AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs) {
            callCount.incrementAndGet();
            return super.invoke(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, timeout,
                    kwargs);
        }
    }
}
