/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashEmbeddingTest {

    @Test
    void embedQueryReturnsDeterministicVector() {
        HashEmbedding embedding = new HashEmbedding();
        List<Double> v1 = embedding.embedQuery("hello").join();
        List<Double> v2 = embedding.embedQuery("hello").join();
        assertEquals(v1, v2);
        assertEquals(32, v1.size());
    }

    @Test
    void embedQueryDifferentTextsDifferentVectors() {
        HashEmbedding embedding = new HashEmbedding();
        List<Double> v1 = embedding.embedQuery("hello").join();
        List<Double> v2 = embedding.embedQuery("world").join();
        assertFalse(v1.equals(v2));
    }

    @Test
    void embedDocumentsReturnsBatchResults() {
        HashEmbedding embedding = new HashEmbedding();
        List<List<Double>> results = embedding.embedDocuments(List.of("a", "b", "c"), null).join();
        assertEquals(3, results.size());
        for (List<Double> vec : results) {
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
        List<Double> nullResult = embedding.embedQuery(null).join();
        List<Double> emptyResult = embedding.embedQuery("").join();
        assertNotNull(nullResult);
        assertEquals(32, nullResult.size());
        assertEquals(nullResult, emptyResult);
    }

    @Test
    void valuesInRangeNeg1To1() {
        HashEmbedding embedding = new HashEmbedding();
        List<Double> vector = embedding.embedQuery("test text for range check").join();
        for (Double value : vector) {
            assertTrue(value >= -1.0d && value <= 1.0d);
        }
    }
}
