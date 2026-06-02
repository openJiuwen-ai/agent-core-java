/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/extractor/test_base.py.
 */
class TestExtractor {

    private static final class ConcreteExtractor extends Extractor {
        @Override
        public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
            java.util.ArrayList<Triple> triples = new java.util.ArrayList<>();
            for (TextChunk chunk : chunks) {
                if (chunk.getText().contains("knows")) {
                    triples.add(new Triple("Alice", "knows", "Bob", null, Map.of("doc_id", chunk.getDocId())));
                }
            }
            return triples;
        }
    }

    @Test
    void testExtract() {
        List<Triple> triples = new ConcreteExtractor().extract(List.of(
                new TextChunk("1", "Alice knows Bob", "doc_1"),
                new TextChunk("2", "Some other text", "doc_1")), Map.of());

        assertEquals(1, triples.size());
        assertEquals("Alice", triples.getFirst().getSubject());
        assertEquals("knows", triples.getFirst().getPredicate());
        assertEquals("Bob", triples.getFirst().getObject());
    }

    @Test
    void testProcess() {
        List<Triple> triples = new ConcreteExtractor().process(
                List.of(new TextChunk("1", "Alice knows Bob", "doc_1")),
                Map.of());

        assertEquals(1, triples.size());
    }

    @Test
    void testCannotInstantiateAbstractClass() {
        assertTrue(Modifier.isAbstract(Extractor.class.getModifiers()));
    }
}
