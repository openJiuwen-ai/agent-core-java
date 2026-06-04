/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for edit check rail.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.rails.test_edit_check_rail}.</p>
 */
@DisplayName("Edit Check Rail Tests")
class TestEditCheckRail {
    @Test
    @DisplayName("blocks out of scope write")
    void testBlocksOutOfScopeWrite() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context("write_file", "openjiuwen/auto_harness/schema.py");

        rail.beforeToolCall(ctx);

        ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
        Map<?, ?> result = (Map<?, ?>) inputs.getToolResult();
        assertTrue(Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool")));
        assertTrue(String.valueOf(result.get("error")).contains("Out-of-scope edit blocked"));
        assertTrue(String.valueOf(result.get("error")).contains("openjiuwen/auto_harness/schema.py"));
    }

    @Test
    @DisplayName("allows in scope write")
    void testAllowsInScopeWrite() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context("write_file", "openjiuwen/harness/cli/ui/renderer.py");

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    @DisplayName("allows source readme markdown")
    void testAllowsSourceReadmeMarkdown() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context("edit_file", "openjiuwen/harness/cli/README.md");

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    @DisplayName("pushes steering on ruff failure")
    void testPushesSteeringOnRuffFailure() {
        EditSafetyRail rail = new EditSafetyRail(3,
                filePath -> new EditSafetyRail.RuffResult(1, "E501 line too long"));
        AgentCallbackContext ctx = context("write_file", "src/foo.py");

        rail.afterToolCall(ctx);

        List<String> steering = steering(ctx);
        assertEquals(1, steering.size());
        assertTrue(steering.get(0).toLowerCase().contains("ruff"));
    }

    @Test
    @DisplayName("no steering on ruff pass")
    void testNoSteeringOnRuffPass() {
        EditSafetyRail rail = new EditSafetyRail(3,
                filePath -> new EditSafetyRail.RuffResult(0, ""));
        AgentCallbackContext ctx = context("edit_file", "src/bar.py");

        rail.afterToolCall(ctx);

        assertEquals(0, steering(ctx).size());
        assertEquals(List.of("src/bar.py"), rail.editedFiles());
    }

    @Test
    @DisplayName("skips non python")
    void testSkipsNonPython() {
        RecordingRuffRunner runner = new RecordingRuffRunner();
        EditSafetyRail rail = new EditSafetyRail(3, runner);
        AgentCallbackContext ctx = context("write_file", "README.md");

        rail.afterToolCall(ctx);

        assertEquals(0, steering(ctx).size());
        assertEquals(0, runner.calls);
    }

    @Test
    @DisplayName("skips non write tool")
    void testSkipsNonWriteTool() {
        RecordingRuffRunner runner = new RecordingRuffRunner();
        EditSafetyRail rail = new EditSafetyRail(3, runner);
        AgentCallbackContext ctx = context("read_file", "src/foo.py");

        rail.afterToolCall(ctx);

        assertEquals(0, steering(ctx).size());
        assertEquals(0, runner.calls);
    }

    @Test
    @DisplayName("handles ruff not found")
    void testHandlesRuffNotFound() {
        EditSafetyRail rail = new EditSafetyRail(3, filePath -> {
            throw new FileNotFoundException("ruff");
        });
        AgentCallbackContext ctx = context("write_file", "src/foo.py");

        rail.afterToolCall(ctx);

        assertEquals(0, steering(ctx).size());
    }

    @Test
    @DisplayName("reset clears edited files")
    void testResetClearsEditedFiles() {
        EditSafetyRail rail = new EditSafetyRail(3,
                filePath -> new EditSafetyRail.RuffResult(0, ""));
        AgentCallbackContext ctx = context("write_file", "src/foo.py");

        rail.afterToolCall(ctx);
        assertEquals(List.of("src/foo.py"), rail.editedFiles());
        rail.reset();

        assertEquals(List.of(), rail.editedFiles());
    }

    private static AgentCallbackContext context(String toolName, String filePath) {
        return AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder()
                        .toolName(toolName)
                        .toolArgs(Map.of("file_path", filePath))
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> steering(AgentCallbackContext ctx) {
        return (List<String>) ctx.getExtra().computeIfAbsent("steering", key -> new ArrayList<String>());
    }

    private static final class RecordingRuffRunner implements EditSafetyRail.RuffRunner {
        private int calls;

        @Override
        public EditSafetyRail.RuffResult run(String filePath) throws IOException {
            calls++;
            return new EditSafetyRail.RuffResult(0, "");
        }
    }
}
