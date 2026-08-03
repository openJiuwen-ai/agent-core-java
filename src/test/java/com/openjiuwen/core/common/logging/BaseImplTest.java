/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseImplTest {

    @Test
    void resolveLogTypeLabelAndEscapeControlCharsMatchPythonHelpers() {
        assertEquals("perf", BaseImpl.resolveLogTypeLabel("performance"));
        assertEquals("agent", BaseImpl.resolveLogTypeLabel("agent"));
        assertEquals("line1\\nline2\\tend", BaseImpl.escapeControlChars("line1\nline2\tend"));
    }

    @Test
    void formatLogFilenameAppliesPatternAndPreservesExtension() {
        String formatted = BaseImpl.formatLogFilename("/tmp/app.log", "{name}-{date}");

        assertTrue(formatted.startsWith("/tmp/app-"));
        assertTrue(formatted.endsWith(".log"));
        assertEquals("hello world", BaseImpl.autoFormatMessage("%s %s", "hello", "world"));
        assertEquals("hello world", BaseImpl.autoFormatMessage("{} {}", "hello", "world"));
    }
}
