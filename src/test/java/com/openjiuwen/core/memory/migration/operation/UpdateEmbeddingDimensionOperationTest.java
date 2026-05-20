/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UpdateEmbeddingDimensionOperationTest {

    @Test
    void exposesPythonStyleRecomputeCallbackAndBatchSize() {
        OperationMetadata metadata = new OperationMetadata(3, "update embedding dimension");
        var func = (java.util.function.Function<Object, Object>) value -> "recomputed:" + value;

        UpdateEmbeddingDimensionOperation operation = new UpdateEmbeddingDimensionOperation(
                metadata, "vector_summary", "embedding", 768, func, 128);

        assertEquals("vector_summary", operation.getDataType());
        assertEquals("embedding", operation.getFieldName());
        assertEquals(768, operation.getNewDimension());
        assertSame(func, operation.getRecomputeEmbeddingFunc());
        assertEquals(128, operation.getBatchSize());
    }

    @Test
    void defaultConstructorKeepsOptionalCallbackNull() {
        UpdateEmbeddingDimensionOperation operation = new UpdateEmbeddingDimensionOperation(
                new OperationMetadata(1), "vector_summary", "embedding", 256, 1000);

        assertNull(operation.getRecomputeEmbeddingFunc());
        assertEquals(1000, operation.getBatchSize());
    }
}
