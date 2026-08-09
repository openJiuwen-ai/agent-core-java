/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentRailAgentBridgeTest {

    @Test
    void beforeModelCallForwardsToDeepAgentRail() {
        AtomicInteger calls = new AtomicInteger();
        DeepAgentRail rail = new DeepAgentRail() {
            @Override
            public void beforeModelCall(CallbackContext ctx) {
                calls.incrementAndGet();
            }
        };
        DeepAgentRailAgentBridge bridge = new DeepAgentRailAgentBridge(new DeepAgent(), rail);

        bridge.beforeModelCall(new AgentCallbackContext()).toCompletableFuture().join();

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void beforeToolCallFlattensToolCallInputsForDeepAgentRail() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Object> toolArgs = new AtomicReference<>();
        DeepAgentRail rail = new DeepAgentRail() {
            @Override
            public void beforeToolCall(CallbackContext ctx) {
                toolName.set(String.valueOf(ctx.get("tool_name")));
                toolArgs.set(ctx.get("tool_args"));
            }
        };
        DeepAgentRailAgentBridge bridge = new DeepAgentRailAgentBridge(new DeepAgent(), rail);
        AgentCallbackContext context = new AgentCallbackContext();
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolCall(ToolCall.builder().id("tc-1").name("bash").arguments("{\"cmd\":\"ls\"}").build());
        inputs.setToolName("bash");
        inputs.setToolArgs(Map.of("cmd", "ls"));
        context.setInputs(inputs);

        bridge.beforeToolCall(context).toCompletableFuture().join();

        assertThat(toolName.get()).isEqualTo("bash");
        assertThat(toolArgs.get()).isEqualTo(Map.of("cmd", "ls"));
    }

    @Test
    void beforeToolCallWritesSkipAndRewritesBackToAgentCallbackContext() {
        DeepAgentRail rail = new DeepAgentRail() {
            @Override
            public void beforeToolCall(CallbackContext ctx) {
                ctx.put("_skip_tool", true);
                ctx.put("tool_name", "read_file");
                ctx.put("tool_args", Map.of("path", "README.md"));
                ctx.put("tool_result", "blocked");
            }
        };
        DeepAgentRailAgentBridge bridge = new DeepAgentRailAgentBridge(new DeepAgent(), rail);
        AgentCallbackContext context = new AgentCallbackContext();
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName("bash");
        inputs.setToolArgs(Map.of("cmd", "ls"));
        context.setInputs(inputs);

        bridge.beforeToolCall(context).toCompletableFuture().join();

        assertThat(context.getExtra().get("_skip_tool")).isEqualTo(true);
        assertThat(inputs.getToolName()).isEqualTo("read_file");
        assertThat(inputs.getToolArgs()).isEqualTo(Map.of("path", "README.md"));
        assertThat(inputs.getToolResult()).isEqualTo("blocked");
    }

    @Test
    void beforeToolCallRejectMapsToSkipTool() {
        DeepAgentRail rail = new DeepAgentRail() {
            @Override
            public void beforeToolCall(CallbackContext ctx) {
                ctx.reject("[PERMISSION_DENIED] blocked");
            }
        };
        DeepAgentRailAgentBridge bridge = new DeepAgentRailAgentBridge(new DeepAgent(), rail);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(new ToolCallInputs());

        bridge.beforeToolCall(context).toCompletableFuture().join();

        assertThat(context.getExtra().get("_skip_tool")).isEqualTo(Boolean.TRUE);
        assertThat(context.getExtra().get("error")).isEqualTo("[PERMISSION_DENIED] blocked");
    }

    @Test
    void beforeModelCallFlattensModelCallInputs() {
        AtomicReference<Object> messages = new AtomicReference<>();
        DeepAgentRail rail = new DeepAgentRail() {
            @Override
            public void beforeModelCall(CallbackContext ctx) {
                messages.set(ctx.get("messages"));
            }
        };
        DeepAgentRailAgentBridge bridge = new DeepAgentRailAgentBridge(new DeepAgent(), rail);
        AgentCallbackContext context = new AgentCallbackContext();
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(List.of("hello"));
        context.setInputs(inputs);

        bridge.beforeModelCall(context).toCompletableFuture().join();

        assertThat(messages.get()).isEqualTo(List.of("hello"));
    }
}
