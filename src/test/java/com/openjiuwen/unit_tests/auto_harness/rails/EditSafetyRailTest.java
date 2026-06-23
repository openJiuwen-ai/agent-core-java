/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.rails.EditSafetyRail.RuffResult;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.ToolCallInputs;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code EditSafetyRail} in
 * {@code openjiuwen/auto_harness/rails/edit_safety_rail.py}.
 */
class EditSafetyRailTest {

    @Test
    void blocksOutOfScopeWrites() {
        EditSafetyRail rail = new EditSafetyRail();
        AgentCallbackContext ctx = context(toolInputs("write_file", Map.of("file_path", "openjiuwen/auto_harness/x.py")));

        rail.beforeToolCall(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(((ToolCallInputs) ctx.getInputs()).getToolResult()).asString().contains("Out-of-scope");
    }

    @Test
    void tracksEditedFilesAndWarnsPastLimit() {
        EditSafetyRail rail = new EditSafetyRail(1, filePath ->
                CompletableFuture.completedFuture(new RuffResult(0, "", true)));
        Queue<String> steering = new ArrayDeque<>();
        AgentCallbackContext first = context(toolInputs("edit_file", Map.of("file_path", "tests/a.py")));
        first.bindSteeringQueue(steering);
        AgentCallbackContext second = context(toolInputs("edit_file", Map.of("file_path", "tests/b.py")));
        second.bindSteeringQueue(steering);

        rail.afterToolCall(first).toCompletableFuture().join();
        rail.afterToolCall(second).toCompletableFuture().join();

        assertThat(rail.editedFiles()).hasSize(2);
        assertThat(steering).anySatisfy(message -> assertThat(message).contains("limit is 1"));
    }

    private static AgentCallbackContext context(Object inputs) {
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setInputs(inputs);
        return ctx;
    }

    private static ToolCallInputs toolInputs(String toolName, Object args) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName(toolName);
        inputs.setToolArgs(args);
        inputs.setToolCall(Map.of("id", "call-1"));
        return inputs;
    }
}
