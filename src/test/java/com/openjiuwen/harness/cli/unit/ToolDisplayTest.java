/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.ToolDisplay;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDisplayTest {

    @Test
    void getDisplayNameMapsKnownTools() {
        assertEquals("Read", ToolDisplay.getDisplayName("read_file"));
        assertEquals("TodoWrite", ToolDisplay.getDisplayName("todo_create"));
    }

    @Test
    void formatToolArgsAndResultsMirrorPythonShape() {
        assertTrue(ToolDisplay.formatToolArgs("read_file", Map.of("file_path", "/tmp/a.py", "limit", 10)).contains("limit=10"));
        assertEquals("hello world", ToolDisplay.formatToolResult("bash", "hello world"));
        assertTrue(ToolDisplay.formatWritePreview("a\nb\nc").contains("1 a"));
    }
}
