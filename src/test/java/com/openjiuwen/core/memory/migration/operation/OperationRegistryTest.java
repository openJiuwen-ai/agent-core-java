/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors focused registry behavior from Python's operation-registry module.
 */
class OperationRegistryTest {

    @Test
    void testRegisterAndRangeLookup() {
        OperationRegistry registry = new OperationRegistry();
        BaseOperation v1 = new TestOperation(1, "v1");
        BaseOperation v3 = new TestOperation(3, "v3");

        registry.register("user_messages", v1);
        registry.register("user_messages", v3);

        assertEquals(List.of(v1, v3), registry.getOperations("user_messages", 1, 3));
        assertEquals(List.of(v3), registry.getOperations("user_messages", 2, 3));
    }

    @Test
    void testRegisterRejectsNonIncreasingVersions() {
        OperationRegistry registry = new OperationRegistry();
        registry.register("user_messages", new TestOperation(2, "v2"));

        assertThrows(BaseError.class, () -> registry.register("user_messages", new TestOperation(2, "duplicate")));
    }

    @Test
    void testGetOperationsHandlesEmptyAndInvertedRanges() {
        OperationRegistry registry = new OperationRegistry();
        assertTrue(registry.getOperations("missing", 1, 2).isEmpty());
        assertTrue(registry.getOperations("missing", 3, 1).isEmpty());
    }

    @Test
    void testVersionEntitiesAndMutationHelpers() {
        OperationRegistry registry = new OperationRegistry();
        BaseOperation operation = new TestOperation(4, "v4");
        registry.register("vector_summary", operation);

        assertEquals(4, registry.getCurrentVersion("vector_summary"));
        assertEquals(List.of("vector_summary"), registry.getAllEntities());
        assertEquals(1, registry.getAllOperations().get("vector_summary").size());

        registry.setOperations(Map.of("kv_global", List.of(new TestOperation(5, "v5"))));
        assertEquals(5, registry.getCurrentVersion("kv_global"));

        registry.clear();
        assertEquals(0, registry.getCurrentVersion("kv_global"));
    }

    private static final class TestOperation extends BaseOperation {
        private TestOperation(int schemaVersion, String description) {
            super(new OperationMetadata(schemaVersion, description));
        }
    }
}
