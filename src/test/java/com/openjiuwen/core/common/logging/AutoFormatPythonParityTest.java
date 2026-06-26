/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/**
 * Tests for auto-detecting placeholder style in log messages.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/common/log/test_auto_format.py}.</p>
 */
class AutoFormatPythonParityTest {

    @Test
    void noArgsReturnsMessage() {
        assertEquals("hello world", fmt("hello world"));
    }

    @Test
    void percentS() {
        assertEquals("hello world", fmt("hello %s", "world"));
    }

    @Test
    void percentD() {
        assertEquals("count: 42", fmt("count: %d", 42));
    }

    @Test
    void percentMultiple() {
        assertEquals("list has 3 items", fmt("%s has %d items", "list", 3));
    }

    @Test
    void bracePositional() {
        assertEquals("hello world", fmt("hello {}", "world"));
    }

    @Test
    void braceIndexed() {
        assertEquals("list has 3 items", fmt("{0} has {1} items", "list", 3));
    }

    @Test
    void braceFormatSpec() {
        assertEquals("pi is 3.14", fmt("pi is {:.2f}", 3.14159));
    }

    @Test
    void braceRepr() {
        assertEquals("value is 'abc'", fmt("value is {!r}", "abc"));
    }

    @Test
    void bracePriorityOverPercent() {
        assertEquals("50% done", fmt("{}% done", 50));
    }

    @Test
    void noPlaceholderWithArgsFallsThrough() {
        assertEquals("no placeholders here", fmt("no placeholders here", "extra"));
    }

    @Test
    void nonStringMessageIsStringified() {
        assertEquals("12345", fmt(12345));
    }

    @Test
    void defaultLoggerPercentStyle() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = makeDefaultLogger(handler);

        logger.info("user %s logged in", "alice");

        assertTrue(handler.messages().contains("user alice logged in"));
    }

    @Test
    void defaultLoggerBraceStyle() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = makeDefaultLogger(handler);

        logger.info("user {} logged in", "bob");

        assertTrue(handler.messages().contains("user bob logged in"));
    }

    @Test
    void defaultLoggerMixedBracePercentPrefersBrace() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = makeDefaultLogger(handler);

        logger.info("{}% complete", 75);

        assertTrue(handler.messages().contains("75% complete"));
    }

    @Test
    void loguruLoggerPercentStyle() {
        CapturingHandler handler = new CapturingHandler();
        LoguruLogger logger = makeLoguruLogger(handler);

        logger.info("user %s logged in", "charlie");

        assertTrue(handler.messages().contains("user charlie logged in"));
    }

    @Test
    void loguruLoggerBraceStyle() {
        CapturingHandler handler = new CapturingHandler();
        LoguruLogger logger = makeLoguruLogger(handler);

        logger.info("user {} logged in", "dave");

        assertTrue(handler.messages().contains("user dave logged in"));
    }

    private static String fmt(Object message, Object... args) {
        return StructuredLoggerMixin.autoFormatMessage(String.valueOf(message), args);
    }

    private static DefaultLogger makeDefaultLogger(CapturingHandler handler) {
        DefaultLogger logger = new DefaultLogger("test_auto_fmt", Map.of("level", "DEBUG", "output", List.of()));
        logger.setLevel(10);
        logger.addHandler(handler);
        return logger;
    }

    private static LoguruLogger makeLoguruLogger(CapturingHandler handler) {
        LoguruLogger logger = new LoguruLogger("test_auto_fmt_loguru", Map.of("output", List.of()));
        logger.setLevel(10);
        logger.addHandler(handler);
        return logger;
    }

    private static final class CapturingHandler extends Handler {
        private final StringBuilder messages = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            messages.append(record.getMessage()).append('\n');
        }

        @Override
        public void flush() {
            // No buffered output.
        }

        @Override
        public void close() {
            messages.setLength(0);
        }

        private String messages() {
            return messages.toString();
        }
    }
}
