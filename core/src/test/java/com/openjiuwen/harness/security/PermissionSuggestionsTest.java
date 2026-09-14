/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSuggestionsTest {

    @Test
    void testBuildExactShellSuggestion() {
        List<PermissionSuggestion> suggestions = PermissionSuggestions.buildPermissionSuggestions(
                "bash",
                Map.of("command", "echo hello"),
                null
        );
        assertEquals(1, suggestions.size());
        assertEquals("exact_command", suggestions.get(0).reason());
        assertEquals("echo hello", suggestions.get(0).pattern());
    }

    @Test
    void testHeredocDoesNotProduceSuggestion() {
        List<PermissionSuggestion> suggestions = PermissionSuggestions.buildShellPermissionSuggestions(
                "bash",
                "python <<'PY'\nprint('x')\nPY",
                null
        );
        assertEquals(0, suggestions.size());
    }

    @Test
    void testBuildPathSuggestion() {
        List<PermissionSuggestion> suggestions = PermissionSuggestions.buildPermissionSuggestions(
                "read_file",
                Map.of("path", "C:/tmp/demo.txt"),
                null
        );
        assertEquals(1, suggestions.size());
        assertEquals("path", suggestions.get(0).matchType());
        assertEquals("C:/tmp/demo.txt", suggestions.get(0).pattern());
    }
}
