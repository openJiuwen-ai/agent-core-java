/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for ReAct tool execution through the agent runtime.
 */
@Tag("system-test")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ToolParallelExecutionSystemTest extends SystemTestSupport {

    private static final String TEST_PROVIDER = "tool-parallel-system-test-provider";
    private static final String SEAT_TOOL = "st_open_seat_massage";
    private static final String AC_TOOL = "st_open_air_conditioner";

    @Test
    @DisplayName("ReActAgent executes multiple tool calls in parallel and preserves observations")
    void testReActAgentExecutesMultipleToolCallsInParallel() {
        CountDownLatch bothToolsStarted = new CountDownLatch(2);
        SequencedToolCallingClient modelClient = SequencedToolCallingClient.withFixedFirstTurn(
                List.of(
                        ToolCall.builder().id("tc-seat").name(SEAT_TOOL).arguments("{}").index(0).build(),
                        ToolCall.builder().id("tc-ac").name(AC_TOOL).arguments("{}").index(1).build()),
                "tool observations: ", TEST_PROVIDER);
        Model model = modelClient.registerSharedModel();
        ReActAgent agent = newAgent(model);
        LocalFunction seatTool = carControlTool(SEAT_TOOL, "seat_massage_on", bothToolsStarted);
        LocalFunction acTool = carControlTool(AC_TOOL, "air_conditioner_on", bothToolsStarted);

        Runner.resourceMgr().addTool(seatTool, null);
        Runner.resourceMgr().addTool(acTool, null);
        agent.getAbilityManager().add(List.of(seatTool.getCard(), acTool.getCard()));

        try {
            String sessionId = trackSessionId("parallel-tools-session");
            InvocationCapture capture = invokeAgent(
                    agent,
                    Map.of(
                            "query", "帮我打开座椅按摩，帮我打开空调",
                            "conversation_id", sessionId
                    ),
                    sessionId
            );

            assertThat(modelClient.modelCallCount()).isEqualTo(2);
            assertThat(modelClient.firstCallToolNames()).contains(SEAT_TOOL, AC_TOOL);
            assertThat(modelClient.observedToolCallIds()).containsExactly("tc-seat", "tc-ac");
            assertThat(modelClient.observedToolMessages())
                    .allMatch(content -> content.contains("parallel=true"))
                    .allMatch(content -> content.contains("session=" + sessionId));
            assertThat(capture.flattenedText())
                    .contains("seat_massage_on:parallel=true")
                    .contains("air_conditioner_on:parallel=true")
                    .contains("tc-seat>tc-ac");
        } finally {
            removeTool(SEAT_TOOL);
            removeTool(AC_TOOL);
        }
    }

    private static ReActAgent newAgent(Model model) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("parallel-tools-agent")
                .name("parallel-tools-agent")
                .description("system test agent for parallel tool calls")
                .build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "You are a deterministic vehicle control assistant."
                )))
                .build());
        agent.setLlm(model);
        return agent;
    }

    private static LocalFunction carControlTool(String toolName, String resultPrefix, CountDownLatch bothToolsStarted) {
        return new LocalFunction(
                ToolCard.builder()
                        .id(toolName)
                        .name(toolName)
                        .description("System-test vehicle control tool")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        ))
                        .build(),
                (LocalFunction.ContextFunction) (inputs, kwargs) -> {
                    bothToolsStarted.countDown();
                    boolean parallel = await(bothToolsStarted, 2, TimeUnit.SECONDS);
                    Session currentSession = SessionContextHolder.getCurrentSession();
                    String sessionId = currentSession != null ? currentSession.getSessionId() : "missing";
                    return resultPrefix + ":parallel=" + parallel + ":session=" + sessionId;
                }
        );
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void removeTool(String toolName) {
        Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
    }
}
