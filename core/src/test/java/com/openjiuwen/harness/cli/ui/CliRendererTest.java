/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRendererTest {

    @Test
    void llmOutputChunksAccumulateVisibleText() {
        CliRenderer renderer = new CliRenderer();
        ByteArrayOutputStream terminalBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();

        CliRenderer.RenderResult result = renderer.renderStream(
                chunks(
                        Map.of("type", "llm_output", "payload", Map.of("content", "Hello")),
                        Map.of("type", "llm_output", "payload", Map.of("content", " world"))
                ),
                new PrintStream(terminalBuffer, true, StandardCharsets.UTF_8),
                new PrintStream(consoleBuffer, true, StandardCharsets.UTF_8)
        );

        assertEquals("Hello world", result.text());
        assertTrue(terminalBuffer.toString(StandardCharsets.UTF_8).contains("Hello world"));
    }

    @Test
    void controllerOutputRendersTaskFailedMessages() {
        CliRenderer renderer = new CliRenderer();
        ByteArrayOutputStream terminalBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();

        renderer.renderStream(
                chunks(Map.of(
                        "type", "controller_output",
                        "payload", Map.of(
                                "type", "task_failed",
                                "data", List.of(Map.of("text", "broken"))
                        )
                )),
                new PrintStream(terminalBuffer, true, StandardCharsets.UTF_8),
                new PrintStream(consoleBuffer, true, StandardCharsets.UTF_8)
        );

        assertTrue(consoleBuffer.toString(StandardCharsets.UTF_8).contains("broken"));
    }

    private static Iterator<Map<String, Object>> chunks(Map<String, Object>... chunks) {
        return List.of(chunks).iterator();
    }
}
