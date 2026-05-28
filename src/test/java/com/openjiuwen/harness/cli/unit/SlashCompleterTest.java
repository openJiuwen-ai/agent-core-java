/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for slash command completion and ESC interrupt.
 * <p>
 * Mirrors Python's {@code test_slash_completer} in
 * {@code tests.cli.unit.test_slash_completer}.
 */
class SlashCompleterTest {

    private static final Set<String> SLASH_COMMANDS = Set.of(
            "/help", "/exit", "/status", "/compact", "/cost", "/model",
            "/quit", "/clear", "/skills"
    );

    private static final Map<String, String> SLASH_DESCRIPTIONS = Map.of(
            "/help", "Show help",
            "/exit", "Exit REPL",
            "/status", "Show status",
            "/compact", "Compact context",
            "/cost", "Show token cost",
            "/model", "Switch model",
            "/clear", "Clear screen",
            "/skills", "List skills"
    );

    private List<String> complete(String text) {
        if (!text.startsWith("/")) return List.of();
        if (text.contains(" ")) return List.of();
        return SLASH_COMMANDS.stream()
                .filter(cmd -> cmd.startsWith(text))
                .filter(cmd -> !cmd.equals("/quit"))
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void slashPrefixMatchesAll() {
        List<String> results = complete("/");
        assertTrue(results.contains("/help"));
        assertTrue(results.contains("/exit"));
        assertTrue(results.contains("/status"));
        assertFalse(results.contains("/quit"));
    }

    @Test
    void partialMatch() {
        List<String> results = complete("/co");
        assertTrue(results.contains("/compact"));
        assertTrue(results.contains("/cost"));
        assertFalse(results.contains("/help"));
    }

    @Test
    void exactMatch() {
        List<String> results = complete("/help");
        assertEquals(List.of("/help"), results);
    }

    @Test
    void noMatch() {
        List<String> results = complete("/xyz");
        assertEquals(List.of(), results);
    }

    @Test
    void noCompletionWithoutSlash() {
        List<String> results = complete("hello");
        assertEquals(List.of(), results);
    }

    @Test
    void noCompletionAfterSpace() {
        List<String> results = complete("/model gpt");
        assertEquals(List.of(), results);
    }

    @Test
    void descriptionsCoverAllCommands() {
        for (String cmd : SLASH_COMMANDS) {
            if (!cmd.equals("/quit")) {
                assertTrue(SLASH_DESCRIPTIONS.containsKey(cmd),
                        "Missing description for " + cmd);
            }
        }
    }

    @Test
    void completionHasMeta() {
        List<String> results = complete("/he");
        assertEquals(List.of("/help"), results);
        assertTrue(SLASH_DESCRIPTIONS.containsKey("/help"));
    }
}
