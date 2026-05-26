/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.CodingMemoryTools;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CodingMemoryTools.
 * <p>
 * Mirrors Python's {@code TestCodingMemoryTools} in
 * {@code tests/unit_tests/core/memory/lite/test_coding_memory_tools.py}.
 */
class TestCodingMemoryTools {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Constants and basic setup)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test constants exist")
    void testConstantsExist() {
        assertNotNull(CodingMemoryTools.CODING_MEMORY_DIR);
        assertEquals("coding_memory", CodingMemoryTools.CODING_MEMORY_DIR);
        assertTrue(CodingMemoryTools.MAX_INDEX_LINES > 0);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test default context is null before binding")
    void testDefaultContextNullBeforeBinding() {
        // Clear any existing context
        CodingMemoryTools.clearCodingMemoryRuntime();
        
        assertNull(CodingMemoryTools.getCodingMemoryContext());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Runtime binding)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test clear coding memory runtime clears context")
    void testClearCodingMemoryRuntime() {
        CodingMemoryTools.clearCodingMemoryRuntime();
        
        assertNull(CodingMemoryTools.getCodingMemoryContext());
        assertNull(CodingMemoryTools.getCodingMemoryWorkspace());
        assertNull(CodingMemoryTools.getCodingMemorySysOperation());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (File path validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test coding memory directory name")
    void testCodingMemoryDirectoryName() {
        String dirName = CodingMemoryTools.CODING_MEMORY_DIR;
        assertTrue(dirName.contains("coding_memory") || dirName.equals("coding_memory"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test max index lines is reasonable")
    void testMaxIndexLinesReasonable() {
        int maxLines = CodingMemoryTools.MAX_INDEX_LINES;
        assertTrue(maxLines >= 10 && maxLines <= 1000, 
                "MAX_INDEX_LINES should be between 10 and 1000 for practical use");
    }
}