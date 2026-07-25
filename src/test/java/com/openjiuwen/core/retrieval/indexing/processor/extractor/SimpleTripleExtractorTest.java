/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTripleExtractorTest {

    @Test
    void extractBasicTriples() {
        SimpleTripleExtractor extractor = new SimpleTripleExtractor();
        List<Triple> triples = extractor.extract(
                List.of(new TextChunk("chunk-1", "Alice knows Bob. Bob likes Carol.", "doc-1")),
                Map.of());

        assertFalse(triples.isEmpty());
        assertEquals("Alice", triples.get(0).getSubject());
        assertEquals("knows", triples.get(0).getPredicate());
        assertEquals("Bob.", triples.get(0).getObject());
        assertEquals("doc-1", triples.get(0).getMetadata().get("doc_id"));
        assertEquals("chunk-1", triples.get(0).getMetadata().get("chunk_id"));
    }

    @Test
    void extractReturnsEmptyForEmptyChunks() {
        SimpleTripleExtractor extractor = new SimpleTripleExtractor();
        List<Triple> triples = extractor.extract(List.of(), Map.of());
        assertTrue(triples.isEmpty());
    }

    @Test
    void extractReturnsEmptyForNullChunks() {
        SimpleTripleExtractor extractor = new SimpleTripleExtractor();
        List<Triple> triples = extractor.extract(null, Map.of());
        assertTrue(triples.isEmpty());
    }

    @Test
    void extractSkipsShortSentences() {
        SimpleTripleExtractor extractor = new SimpleTripleExtractor();
        List<Triple> triples = extractor.extract(
                List.of(new TextChunk("chunk-1", "Hello. OK.", "doc-1")),
                Map.of());
        // Short sentences with less than 3 tokens should be skipped
        assertTrue(triples.isEmpty());
    }

    @Test
    void extractMultipleChunks() {
        SimpleTripleExtractor extractor = new SimpleTripleExtractor();
        List<Triple> triples = extractor.extract(
                List.of(
                        new TextChunk("chunk-1", "Alice knows Bob.", "doc-1"),
                        new TextChunk("chunk-2", "Carol visits Paris.", "doc-2")),
                Map.of());

        assertTrue(triples.size() >= 2);
    }
}
