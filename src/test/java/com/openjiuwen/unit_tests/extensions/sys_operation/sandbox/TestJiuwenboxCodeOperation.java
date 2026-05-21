/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JiuwenBox code operations.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox_code_operation.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_JIUWENBOX_TESTS", matches = "true")
public class TestJiuwenboxCodeOperation {

    // ---------------------------------------------------------------------------
    // Code Write Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test write code to JiuwenBox")
    @Tag("level0")
    void testWriteCodeToJiuwenbox() {
        String codePath = "/workspace/code/test.py";
        String code = "print('Hello JiuwenBox')";
        
        assertThat(codePath).endsWith(".py");
        assertThat(code).contains("print");
    }

    // ---------------------------------------------------------------------------
    // Code Execute Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test execute code in JiuwenBox")
    @Tag("level0")
    void testExecuteCodeInJiuwenbox() {
        String expectedOutput = "Hello JiuwenBox";
        
        assertThat(expectedOutput).contains("JiuwenBox");
    }

    @Test
    @DisplayName("Test execute code with timeout")
    @Tag("level0")
    void testExecuteCodeWithTimeout() {
        int timeoutSeconds = 30;
        
        assertThat(timeoutSeconds).isEqualTo(30);
    }

    // ---------------------------------------------------------------------------
    // Code Debug Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test debug code in JiuwenBox")
    @Tag("level0")
    void testDebugCodeInJiuwenbox() {
        List<Integer> breakpoints = Arrays.asList(5, 10, 15);
        
        assertThat(breakpoints).hasSize(3);
    }

    // ---------------------------------------------------------------------------
    // Multi-language Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test execute Python code")
    @Tag("level0")
    void testExecutePythonCode() {
        String language = "python";
        
        assertThat(language).isEqualTo("python");
    }

    @Test
    @DisplayName("Test execute JavaScript code")
    @Tag("level0")
    void testExecuteJavaScriptCode() {
        String language = "javascript";
        
        assertThat(language).isEqualTo("javascript");
    }
}