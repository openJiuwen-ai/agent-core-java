/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.common;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retrieval result data model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/common/test_retrieval_result.py
 */
class TestSearchResult {

    @Test
    void testCreateSearchResult() {
        // Test creating search result
        SearchResult result = new SearchResult("result_1", "Test result", 0.95);
        assertEquals("result_1", result.getId());
        assertEquals("Test result", result.getText());
        assertEquals(0.95, result.getScore());
        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void testCreateSearchResultWithMetadata() {
        // Test creating search result with metadata
        Map<String, Object> metadata = Map.of("doc_id", "doc_1", "source", "test");
        SearchResult result = new SearchResult("result_1", "Test result", 0.95, metadata);
        assertEquals(metadata, result.getMetadata());
    }

    @Test
    void testMissingRequiredFieldsId() {
        // Test missing required fields - id
        assertThrows(BaseError.class, () -> new SearchResult(null, "Test result", 0.95));
    }

    @Test
    void testMissingRequiredFieldsText() {
        // Test missing required fields - text
        assertThrows(BaseError.class, () -> new SearchResult("result_1", null, 0.95));
    }

    @Test
    void testMissingRequiredFieldsScore() {
        // Test missing required fields - score
        assertThrows(BaseError.class, () -> new SearchResult("result_1", "Test result", null));
    }
}

class TestRetrievalResult {

    @Test
    void testCreateRetrievalResult() {
        // Test creating retrieval result
        RetrievalResult result = new RetrievalResult("Test result", 0.95);
        assertEquals("Test result", result.getText());
        assertEquals(0.95, result.getScore());
        assertTrue(result.getMetadata().isEmpty());
        assertNull(result.getDocId());
        assertNull(result.getChunkId());
    }

    @Test
    void testCreateRetrievalResultWithAllFields() {
        // Test creating retrieval result with all fields
        Map<String, Object> metadata = Map.of("source", "test");
        RetrievalResult result = new RetrievalResult(
                "Test result",
                0.95,
                metadata,
                "doc_1",
                "chunk_1"
        );
        assertEquals("Test result", result.getText());
        assertEquals(0.95, result.getScore());
        assertEquals(metadata, result.getMetadata());
        assertEquals("doc_1", result.getDocId());
        assertEquals("chunk_1", result.getChunkId());
    }

    @Test
    void testMissingRequiredFieldsText() {
        // Test missing required fields - text
        assertThrows(BaseError.class, () -> new RetrievalResult(null, 0.95));
    }

    @Test
    void testMissingRequiredFieldsScore() {
        // Test missing required fields - score
        assertThrows(BaseError.class, () -> new RetrievalResult("Test result", null));
    }
}
