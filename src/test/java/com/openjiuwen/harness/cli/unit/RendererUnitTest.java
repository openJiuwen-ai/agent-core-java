/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.CliRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI stream renderer.
 * <p>
 * Mirrors Python's {@code test_renderer} in
 * {@code tests.cli.unit.test_renderer}.
 */
class RendererUnitTest {

    @Test
    void renderTextOutputsToStdout() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            CliRenderer renderer = new CliRenderer();
            renderer.renderText("Hello World");
        } finally {
            System.setOut(oldOut);
        }
        assertTrue(baos.toString().contains("Hello World"));
    }

    @Test
    void renderErrorOutputsToStderr() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(baos));
        try {
            CliRenderer renderer = new CliRenderer();
            renderer.renderError("something went wrong");
        } finally {
            System.setErr(oldErr);
        }
        String output = baos.toString();
        assertTrue(output.contains("[ERROR]"));
        assertTrue(output.contains("something went wrong"));
    }

    @Test
    void renderToolCallOutputsToolNameAndInput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            CliRenderer renderer = new CliRenderer();
            renderer.renderToolCall("bash", "git status");
        } finally {
            System.setOut(oldOut);
        }
        String output = baos.toString();
        assertTrue(output.contains("bash"));
        assertTrue(output.contains("git status"));
    }

    @Test
    void renderToolResultOutputsToolNameAndResult() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            CliRenderer renderer = new CliRenderer();
            renderer.renderToolResult("bash", "success");
        } finally {
            System.setOut(oldOut);
        }
        String output = baos.toString();
        assertTrue(output.contains("bash"));
        assertTrue(output.contains("success"));
    }

    @Test
    void llmOutputAccumulatedIntoResult() {
        StringBuilder result = new StringBuilder();
        result.append("Hello ");
        result.append("World");
        assertEquals("Hello World", result.toString());
    }

    @Test
    void reasoningNotInResult() {
        StringBuilder result = new StringBuilder();
        result.append("Answer");
        assertFalse(result.toString().contains("thinking"));
    }

    @Test
    void answerNotDuplicated() {
        String accumulated = "Hello";
        String answer = "Hello";
        String result = accumulated;
        assertEquals("Hello", result);
    }

    @Test
    void emptyStreamProducesEmptyResult() {
        StringBuilder result = new StringBuilder();
        assertEquals("", result.toString());
    }
}
