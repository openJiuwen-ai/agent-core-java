/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.common;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.RetrievalResult;

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
        assertThrows(IllegalArgumentException.class, () -> new SearchResult());
    }

    @Test
    void testMissingRequiredFieldsIdOnly() {
        // Test missing required fields - id provided but not text
        assertThrows(IllegalArgumentException.class, () -> {
            SearchResult result = new SearchResult();
            result.setId("result_1");
        });
    }

    @Test
    void testMissingRequiredFieldsIdAndText() {
        // Test missing required fields - id and text provided but not score
        SearchResult result = new SearchResult();
        result.setId("result_1");
        result.setText("Test result");
        // score defaults to 0.0, which is valid
        assertEquals(0.0, result.getScore());
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
        assertThrows(IllegalArgumentException.class, () -> new RetrievalResult());
    }

    @Test
    void testMissingRequiredFieldsTextOnly() {
        // Test missing required fields - text provided but not score
        RetrievalResult result = new RetrievalResult();
        result.setText("Test result");
        // score defaults to 0.0, which is valid
        assertEquals(0.0, result.getScore());
    }
}