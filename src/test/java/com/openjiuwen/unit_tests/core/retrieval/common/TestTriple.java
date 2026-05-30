/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.common;

import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Triple data model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/common/test_triple.py
 */
class TestTriple {

    @Test
    void testCreateTriple() {
        // Test creating triple
        Triple triple = new Triple("Alice", "knows", "Bob");
        assertEquals("Alice", triple.getSubject());
        assertEquals("knows", triple.getPredicate());
        assertEquals("Bob", triple.getObject());
        assertTrue(triple.getMetadata().isEmpty());
    }

    @Test
    void testCreateTripleWithMetadata() {
        // Test creating triple with metadata
        Map<String, Object> metadata = Map.of("source", "test", "doc_id", "doc_1");
        Triple triple = new Triple("Alice", "knows", "Bob", null, metadata);
        assertEquals(metadata, triple.getMetadata());
    }

    @Test
    void testCreateTripleWithAllFields() {
        // Test creating triple with all fields
        Map<String, Object> metadata = Map.of("source", "test");
        Triple triple = new Triple("Alice", "knows", "Bob", 0.95, metadata);
        assertEquals("Alice", triple.getSubject());
        assertEquals("knows", triple.getPredicate());
        assertEquals("Bob", triple.getObject());
        assertEquals(0.95, triple.getConfidence());
        assertEquals(metadata, triple.getMetadata());
    }

    @Test
    void testMissingRequiredFieldsAll() {
        // Test missing required fields - all missing
        assertThrows(BaseError.class, () -> new Triple(null, null, null));
    }

    @Test
    void testMissingRequiredFieldsSubjectOnly() {
        // Test missing required fields - subject only
        assertThrows(BaseError.class, () -> new Triple("Alice", null, null));
    }

    @Test
    void testMissingRequiredFieldsSubjectAndPredicate() {
        // Test missing required fields - subject and predicate provided, object missing
        assertThrows(BaseError.class, () -> new Triple("Alice", "knows", null));
    }
}
