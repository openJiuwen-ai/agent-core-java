/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests.unit_tests.core.retrieval.common.test_retrieval_result} in
 * {@code tests/unit_tests/core/retrieval/common/test_retrieval_result.py}.
 */
class RetrievalResultPythonParityTest {

    @Test
    void testCreateSearchResult() {
        SearchResult result = new SearchResult("result_1", "Test result", 0.95d, null);

        assertEquals("result_1", result.getId());
        assertEquals("Test result", result.getText());
        assertEquals(0.95d, result.getScore());
        assertEquals(Map.of(), result.getMetadata());
    }

    @Test
    void testCreateSearchResultWithMetadata() {
        Map<String, Object> metadata = Map.of("doc_id", "doc_1", "source", "test");

        SearchResult result = new SearchResult("result_1", "Test result", 0.95d, metadata);

        assertEquals(metadata, result.getMetadata());
    }

    @Test
    void testSearchResultMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, SearchResult::new);
        assertThrows(IllegalArgumentException.class, () -> new SearchResult("result_1"));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult("result_1", "Test result"));
    }

    @Test
    void testCreateRetrievalResult() {
        RetrievalResult result = new RetrievalResult("Test result", 0.95d, null, null, null);

        assertEquals("Test result", result.getText());
        assertEquals(0.95d, result.getScore());
        assertEquals(Map.of(), result.getMetadata());
        assertNull(result.getDocId());
        assertNull(result.getChunkId());
    }

    @Test
    void testCreateRetrievalResultWithAllFields() {
        Map<String, Object> metadata = Map.of("source", "test");

        RetrievalResult result = new RetrievalResult("Test result", 0.95d, metadata, "doc_1", "chunk_1");

        assertEquals("Test result", result.getText());
        assertEquals(0.95d, result.getScore());
        assertEquals(metadata, result.getMetadata());
        assertEquals("doc_1", result.getDocId());
        assertEquals("chunk_1", result.getChunkId());
    }

    @Test
    void testRetrievalResultMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, RetrievalResult::new);
        assertThrows(IllegalArgumentException.class, () -> new RetrievalResult("Test result"));
    }
}
