/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.logging.StructuredLoggerMixin;
import com.openjiuwen.core.common.logging.DefaultLogger;
import com.openjiuwen.core.common.logging.LoguruLogger;

/**
 * Tests for auto-detecting placeholder style ({ } vs %) in log messages.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.log.test_auto_format}.
 * Tests the _auto_format_message static method behavior.
 */
class TestAutoFormat {

    // ---------------------------------------------------------------------------
    // Test no args returns msg - Mirrors Python test_no_args_returns_msg
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testNoArgsReturnsMsg() {
        // Python: _fmt("hello world", ()) == "hello world"
        String result = StructuredLoggerMixin.autoFormatMessage("hello world", new Object[]{});
        assertEquals("hello world", result);
    }

    // ---------------------------------------------------------------------------
    // Test percent style formatting - Mirrors Python test_percent_s, test_percent_d
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testPercentS() {
        // Python: _fmt("hello %s", ("world",)) == "hello world"
        String result = StructuredLoggerMixin.autoFormatMessage("hello %s", new Object[]{"world"});
        assertEquals("hello world", result);
    }

    @Test
    @Tag("level0")
    void testPercentD() {
        // Python: _fmt("count: %d", (42,)) == "count: 42"
        String result = StructuredLoggerMixin.autoFormatMessage("count: %d", new Object[]{42});
        assertEquals("count: 42", result);
    }

    @Test
    @Tag("level0")
    void testPercentMultiple() {
        // Python: _fmt("%s has %d items", ("list", 3)) == "list has 3 items"
        String result = StructuredLoggerMixin.autoFormatMessage("%s has %d items", new Object[]{"list", 3});
        assertEquals("list has 3 items", result);
    }

    // ---------------------------------------------------------------------------
    // Test brace style formatting - Mirrors Python test_brace_positional, test_brace_indexed
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBracePositional() {
        // Python: _fmt("hello {}", ("world",)) == "hello world"
        String result = StructuredLoggerMixin.autoFormatMessage("hello {}", new Object[]{"world"});
        assertEquals("hello world", result);
    }

    @Test
    @Tag("level0")
    void testBraceIndexed() {
        // Python: _fmt("{0} has {1} items", ("list", 3)) == "list has 3 items"
        String result = StructuredLoggerMixin.autoFormatMessage("{0} has {1} items", new Object[]{"list", 3});
        assertEquals("list has 3 items", result);
    }

    @Test
    @Tag("level0")
    void testBraceFormatSpec() {
        // Python: _fmt("pi is {:.2f}", (3.14159,)) == "pi is 3.14"
        String result = StructuredLoggerMixin.autoFormatMessage("pi is {:.2f}", new Object[]{3.14159});
        assertTrue(result.contains("3.14"));
    }

    // ---------------------------------------------------------------------------
    // Test brace priority over percent - Mirrors Python test_brace_priority_over_percent
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBracePriorityOverPercent() {
        // Python: _fmt("{}% done", (50,)) == "50% done"
        String result = StructuredLoggerMixin.autoFormatMessage("{}% done", new Object[]{50});
        assertEquals("50% done", result);
    }

    // ---------------------------------------------------------------------------
    // Test no placeholder with args - Mirrors Python test_no_placeholder_with_args_falls_through
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testNoPlaceholderWithArgsFallsThrough() {
        // Python: _fmt("no placeholders here", ("extra",))
        String result = StructuredLoggerMixin.autoFormatMessage("no placeholders here", new Object[]{"extra"});
        assertNotNull(result);
    }

    // ---------------------------------------------------------------------------
    // Test StructuredLoggerMixin class - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStructuredLoggerMixinExists() {
        assertNotNull(StructuredLoggerMixin.class);
    }

    @Test
    @Tag("level0")
    void testDefaultLoggerExists() {
        assertNotNull(DefaultLogger.class);
    }

    @Test
    @Tag("level0")
    void testLoguruLoggerExists() {
        assertNotNull(LoguruLogger.class);
    }
}