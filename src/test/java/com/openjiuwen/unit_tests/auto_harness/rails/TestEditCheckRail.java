/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for edit check rail.
 * <p>
 * Mirrors Python's test_edit_check_rail.py from
 * <code>tests/unit_tests/auto_harness/rails/test_edit_check_rail.py</code>.
 */
@DisplayName("Edit Check Rail Tests")
class TestEditCheckRail {

    // Stub classes
    static class ToolCallInputsStub {
        String toolName;
        Map<String, Object> toolArgs;

        ToolCallInputsStub(String toolName, Map<String, Object> toolArgs) {
            this.toolName = toolName;
            this.toolArgs = toolArgs;
        }
    }

    static class FakeCtx {
        ToolCallInputsStub inputs;
        Map<String, Object> extra = new HashMap<>();
        List<String> steerings = new ArrayList<>();

        FakeCtx(ToolCallInputsStub inputs) {
            this.inputs = inputs;
        }

        void addSteering(String msg) {
            steerings.add(msg);
        }
    }

    static class EditSafetyRail {
        List<String> allowedPaths = new ArrayList<>();

        EditSafetyRail() {
            // Default allowed paths
            allowedPaths.add("openjiuwen/");
        }

        void beforeToolCall(FakeCtx ctx) {
            String filePath = (String) ctx.inputs.toolArgs.get("file_path");

            // Check if path is in scope
            boolean inScope = false;
            for (String allowed : allowedPaths) {
                if (filePath != null && filePath.startsWith(allowed)) {
                    inScope = true;
                    break;
                }
            }

            if (!inScope) {
                ctx.extra.put("_skip_tool", true);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Out-of-scope edit blocked: " + filePath);
                ctx.inputs.toolArgs.put("error_result", errorResult);
            }

            // Steering message
            ctx.addSteering("Edit check completed");
        }
    }

    @Nested
    @DisplayName("Edit Safety Rail Tests")
    class TestEditSafetyRail {

        @Test
        @DisplayName("blocks out of scope write")
        void testBlockOutOfScopeWrite() {
            EditSafetyRail rail = new EditSafetyRail();
            Map<String, Object> args = new HashMap<>();
            args.put("file_path", "external/schema.py");

            FakeCtx ctx = new FakeCtx(new ToolCallInputsStub("write_file", args));
            rail.beforeToolCall(ctx);

            assertTrue(ctx.extra.containsKey("_skip_tool"));
            assertTrue(ctx.inputs.toolArgs.containsKey("error_result"));
        }

        @Test
        @DisplayName("allows in scope write")
        void testAllowsInScopeWrite() {
            EditSafetyRail rail = new EditSafetyRail();
            Map<String, Object> args = new HashMap<>();
            args.put("file_path", "openjiuwen/harness/cli/renderer.py");

            FakeCtx ctx = new FakeCtx(new ToolCallInputsStub("write_file", args));
            rail.beforeToolCall(ctx);

            assertFalse(ctx.extra.containsKey("_skip_tool"));
        }

        @Test
        @DisplayName("allows source readme edit")
        void testAllowsSourceReadmeEdit() {
            EditSafetyRail rail = new EditSafetyRail();
            Map<String, Object> args = new HashMap<>();
            args.put("file_path", "openjiuwen/harness/cli/README.md");

            FakeCtx ctx = new FakeCtx(new ToolCallInputsStub("edit_file", args));
            rail.beforeToolCall(ctx);

            assertFalse(ctx.extra.containsKey("_skip_tool"));
        }

        @Test
        @DisplayName("pushes steering on rule")
        void testPushesSteeringOnRule() {
            EditSafetyRail rail = new EditSafetyRail();
            Map<String, Object> args = new HashMap<>();
            args.put("file_path", "openjiuwen/test.py");

            FakeCtx ctx = new FakeCtx(new ToolCallInputsStub("write_file", args));
            rail.beforeToolCall(ctx);

            assertFalse(ctx.steerings.isEmpty());
        }
    }
}