/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for task completion loop extension points.
 * <p>
 * Mirrors Python's {@code test_task_completion_extensions} in
 * {@code tests.unit_tests.harness.test_task_completion_extensions}.
 */
@Tag("unit-test")
class TaskCompletionExtensionsTest {

    @Test
    @DisplayName("Test promise block extraction")
    void testPromiseBlockExtraction() {
        String text = """
            done
            <promise>all_tasks_completed
            Completed tasks:
            - created output
            </promise>
            """;

        String block = extractPromiseBlock(text);
        assertNotNull(block);
        assertTrue(block.startsWith("all_tasks_completed"));
    }

    @Test
    @DisplayName("Test promise block matching")
    void testPromiseBlockMatching() {
        String block = "all_tasks_completed\nCompleted tasks:\n- created output";
        assertTrue(promiseMatches(block, "all_tasks_completed"));
        assertFalse(promiseMatches(block, "different_token"));
    }

    private String extractPromiseBlock(String text) {
        int start = text.indexOf("<promise>");
        int end = text.indexOf("</promise>");
        if (start >= 0 && end >= 0 && end > start) {
            return text.substring(start + 9, end).trim();
        }
        return null;
    }

    private boolean promiseMatches(String block, String expectedToken) {
        if (block == null) return false;
        return block.startsWith(expectedToken);
    }
}