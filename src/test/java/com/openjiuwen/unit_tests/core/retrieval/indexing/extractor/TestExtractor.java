/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extractor abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/extractor/test_base.py
 */
class TestExtractor {

    /**
     * Concrete extractor implementation for testing.
     */
    static class ConcreteExtractor extends Extractor {

        @Override
        public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options) {
            return new ArrayList<>();
        }
    }

    @Test
    void testExtractorClass() {
        // Test that Extractor class exists
        assertNotNull(Extractor.class);
    }

    @Test
    void testConcreteExtractor() {
        // Test concrete implementation
        ConcreteExtractor extractor = new ConcreteExtractor();
        List<TextChunk> input = List.of(
            new TextChunk("chunk_1", "Test chunk", "doc_1")
        );
        List<Triple> result = extractor.extract(input, null);
        assertNotNull(result);
    }
}