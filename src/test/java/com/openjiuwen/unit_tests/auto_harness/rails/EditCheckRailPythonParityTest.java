/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.rails.EditSafetyRail.RuffResult;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.ToolCallInputs;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for edit safety rail write-scope and ruff checks.
 *
 * <p>Mirrors Python's {@code TestEditSafetyRail} in
 * {@code tests/unit_tests/auto_harness/rails/test_edit_check_rail.py}.</p>
 */
class EditCheckRailPythonParityTest {

    @Test
    void testBlocksOutOfScopeWrite() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context(toolInputs(
                "write_file",
                Map.of("file_path", "openjiuwen/auto_harness/schema.py")
        ));

        rail.beforeToolCall(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra()).containsEntry("_skip_tool", true);
        Object result = ((ToolCallInputs) ctx.getInputs()).getToolResult();
        assertThat(result).asString().contains("Out-of-scope edit blocked");
        assertThat(result).asString().contains("openjiuwen/auto_harness/schema.py");
    }

    @Test
    void testAllowsInScopeWrite() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context(toolInputs(
                "write_file",
                Map.of("file_path", "openjiuwen/harness/cli/ui/renderer.py")
        ));

        rail.beforeToolCall(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
    }

    @Test
    void testAllowsSourceReadmeMarkdown() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context(toolInputs(
                "edit_file",
                Map.of("file_path", "openjiuwen/harness/cli/README.md")
        ));

        rail.beforeToolCall(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
    }

    @Test
    void testPushesSteeringOnRuffFailure() {
        RecordingChecker checker = new RecordingChecker(new RuffResult(
                1,
                "src/foo.py:1:1: E501 line too long",
                true
        ));
        EditSafetyRail rail = new EditSafetyRail(3, checker::check);
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext ctx = context(toolInputs("write_file", Map.of("file_path", "src/foo.py")));
        ctx.bindSteeringQueue(steering);

        rail.afterToolCall(ctx).toCompletableFuture().join();

        assertThat(checker.calls).isEqualTo(1);
        assertThat(steering).hasSize(1);
        assertThat(steering.peek()).containsIgnoringCase("ruff");
    }

    @Test
    void testNoSteeringOnRuffPass() {
        RecordingChecker checker = new RecordingChecker(new RuffResult(0, "", true));
        EditSafetyRail rail = new EditSafetyRail(3, checker::check);
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext ctx = context(toolInputs("edit_file", Map.of("file_path", "src/bar.py")));
        ctx.bindSteeringQueue(steering);

        rail.afterToolCall(ctx).toCompletableFuture().join();

        assertThat(steering).isEmpty();
        assertThat(rail.editedFiles()).containsExactly("src/bar.py");
        assertThat(checker.calls).isEqualTo(1);
    }

    @Test
    void testSkipsNonPython() {
        RecordingChecker checker = new RecordingChecker(new RuffResult(1, "should-not-run", true));
        EditSafetyRail rail = new EditSafetyRail(3, checker::check);
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext ctx = context(toolInputs("write_file", Map.of("file_path", "README.md")));
        ctx.bindSteeringQueue(steering);

        rail.afterToolCall(ctx).toCompletableFuture().join();

        assertThat(steering).isEmpty();
        assertThat(checker.calls).isZero();
    }

    @Test
    void testSkipsNonWriteTool() {
        RecordingChecker checker = new RecordingChecker(new RuffResult(1, "should-not-run", true));
        EditSafetyRail rail = new EditSafetyRail(3, checker::check);
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext ctx = context(toolInputs("read_file", Map.of("file_path", "src/foo.py")));
        ctx.bindSteeringQueue(steering);

        rail.afterToolCall(ctx).toCompletableFuture().join();

        assertThat(steering).isEmpty();
        assertThat(checker.calls).isZero();
    }

    @Test
    void testHandlesRuffNotFound() {
        RecordingChecker checker = new RecordingChecker(RuffResult.unavailable());
        EditSafetyRail rail = new EditSafetyRail(3, checker::check);
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext ctx = context(toolInputs("write_file", Map.of("file_path", "src/foo.py")));
        ctx.bindSteeringQueue(steering);

        rail.afterToolCall(ctx).toCompletableFuture().join();

        assertThat(steering).isEmpty();
        assertThat(checker.calls).isEqualTo(1);
    }

    @Test
    void testResetClearsEditedFiles() {
        EditSafetyRail rail = new EditSafetyRail(3, filePath ->
                CompletableFuture.completedFuture(new RuffResult(0, "", true)));
        AgentCallbackContext ctx = context(toolInputs("write_file", Map.of("file_path", "src/foo.py")));

        rail.afterToolCall(ctx).toCompletableFuture().join();
        assertThat(rail.editedFiles()).containsExactly("src/foo.py");
        rail.reset();
        assertThat(rail.editedFiles()).isEmpty();
    }

    private static AgentCallbackContext context(Object inputs) {
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        return context;
    }

    private static ToolCallInputs toolInputs(String toolName, Object args) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName(toolName);
        inputs.setToolArgs(args);
        inputs.setToolCall(Map.of("id", "call-1"));
        return inputs;
    }

    private static final class RecordingChecker {
        private final RuffResult result;
        private int calls;

        private RecordingChecker(RuffResult result) {
            this.result = result;
        }

        private CompletableFuture<RuffResult> check(String filePath) {
            calls++;
            return CompletableFuture.completedFuture(result);
        }
    }
}
