/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

/**
 * CLI output renderer.
 * <p>
 * Mirrors Python's {@code renderer} in
 * {@code openjiuwen.harness.cli.ui.renderer}.
 */
public class CliRenderer {

    /** Render a text message to console. */
    public void renderText(String text) {
        System.out.println(text);
    }

    /** Render an error message. */
    public void renderError(String error) {
        System.err.println("[ERROR] " + error);
    }

    /** Render a tool call. */
    public void renderToolCall(String toolName, String input) {
        System.out.println("[Tool: " + toolName + "] " + input);
    }

    /** Render a tool result. */
    public void renderToolResult(String toolName, Object result) {
        System.out.println("[Result: " + toolName + "] " + result);
    }
}
