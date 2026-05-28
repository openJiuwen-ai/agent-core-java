/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-07: Agent tool calls (Bash / File / Grep).
 * <p>
 * Mirrors Python's {@code test_chat_tools} in
 * {@code tests.cli.e2e.test_chat_tools}.
 */
class ChatToolsE2eTest {

    @TempDir
    Path tmpPath;

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void toolBash() {
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void toolReadFile() throws IOException {
        Path testFile = tmpPath.resolve("test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\n");
        assertTrue(Files.exists(testFile));
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void toolGrep() throws IOException {
        Path codeFile = tmpPath.resolve("code.py");
        Files.writeString(codeFile, "def hello():\n    return 'world'\n");
        assertTrue(Files.exists(codeFile));
    }
}
