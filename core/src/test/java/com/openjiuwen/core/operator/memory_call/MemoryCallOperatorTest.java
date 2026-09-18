/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/core/operator/memory_call/base.py}.
 */
class MemoryCallOperatorTest {

    @Test
    void exposesExpectedTunablesAndDefaults() {
        MemoryCallOperator operator = new MemoryCallOperator();

        assertEquals("memory_call", operator.getOperatorId());
        assertTrue(operator.getTunables().containsKey("enabled"));
        assertTrue(operator.getTunables().containsKey("max_retries"));
        assertTrue(operator.isEnabled());
        assertEquals(0, operator.getMaxRetries());
    }

    @Test
    void clampsRetriesAndNotifiesCallback() {
        List<String> updates = new ArrayList<>();
        MemoryCallOperator operator = new MemoryCallOperator("memory_call", (target, value) -> updates.add(target + "=" + value));

        operator.setParameter("enabled", false);
        operator.setParameter("max_retries", 99);

        assertFalse(operator.isEnabled());
        assertEquals(5, operator.getMaxRetries());
        assertEquals(List.of("enabled=false", "max_retries=5"), updates);
    }

    @Test
    void loadStateRestoresKnownFields() {
        MemoryCallOperator operator = new MemoryCallOperator();

        operator.loadState(Map.of("enabled", false, "max_retries", -3));

        assertFalse(operator.isEnabled());
        assertEquals(0, operator.getMaxRetries());
    }
}
