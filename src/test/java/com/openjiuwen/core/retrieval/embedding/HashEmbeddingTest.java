/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HashEmbeddingTest {

    @Test
    void embedQueryReturnsDeterministicVector() {
        HashEmbedding embedding = new HashEmbedding();
        List<Float> v1 = embedding.embedQuery("hello");
        List<Float> v2 = embedding.embedQuery("hello");
        assertEquals(v1, v2);
        assertEquals(32, v1.size());
    }

    @Test
    void embedQueryDifferentTextsDifferentVectors() {
        HashEmbedding embedding = new HashEmbedding();
        List<Float> v1 = embedding.embedQuery("hello");
        List<Float> v2 = embedding.embedQuery("world");
        assertFalse(v1.equals(v2));
    }

    @Test
    void embedDocumentsReturnsBatchResults() {
        HashEmbedding embedding = new HashEmbedding();
        List<List<Float>> results = embedding.embedDocuments(List.of("a", "b", "c"), null);
        assertEquals(3, results.size());
        for (List<Float> vec : results) {
            assertEquals(32, vec.size());
        }
    }

    @Test
    void getDimensionReturnsConfiguredValue() {
        HashEmbedding defaultEmb = new HashEmbedding();
        assertEquals(32, defaultEmb.getDimension());

        HashEmbedding custom = new HashEmbedding(64, 256);
        assertEquals(64, custom.getDimension());
    }

    @Test
    void embedQueryHandlesNullAndEmpty() {
        HashEmbedding embedding = new HashEmbedding();
        List<Float> nullResult = embedding.embedQuery(null);
        List<Float> emptyResult = embedding.embedQuery("");
        assertNotNull(nullResult);
        assertEquals(32, nullResult.size());
        assertEquals(nullResult, emptyResult);
    }

    @Test
    void valuesInRangeNeg1To1() {
        HashEmbedding embedding = new HashEmbedding();
        List<Float> vector = embedding.embedQuery("test text for range check");
        for (Float value : vector) {
            assertTrue(value >= -1.0f && value <= 1.0f);
        }
    }
}
