/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verifies ReActAgent wires batch tool calls into AbilityManager parallel execution.
 */
class ReActAgentToolParallelTest {

    private final List<String> toolIds = new ArrayList<>();
    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ReActAgent(AgentCard.builder()
                .id("react-parallel-ut")
                .name("react-parallel-ut")
                .description("unit test agent")
                .build());
        agent.configure(ReActAgentConfig.builder().maxIterations(2).parallelToolCalls(true).build());
    }

    @AfterEach
    void tearDown() {
        for (String toolId : toolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
        toolIds.clear();
        SessionContextHolder.clearCurrentSession();
    }

    @Test
    @DisplayName("ReActAgent 将多 ToolCall 交给 AbilityManager 并行执行")
    void executeMultipleToolCallsViaAbilityManagerParallel() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        String firstId = registerBlockingTool("parallel-first", bothStarted);
        String secondId = registerBlockingTool("parallel-second", bothStarted);

        AgentSession session = AgentSession.createAgentSession("parallel-ut-session", null, agent.getCard());
        long start = System.nanoTime();
        List<AbilityManager.ExecutionResult> results = invokeExecuteToolCalls(
                List.of(
                        ToolCall.builder().id("tc-1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name(secondId).arguments("{}").build()
                ),
                session
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(results).hasSize(2);
        assertThat(String.valueOf(results.get(0).result())).contains("parallel=true");
        assertThat(String.valueOf(results.get(1).result())).contains("parallel=true");
        assertThat(elapsedMillis).isLessThan(1500L);
    }

    @Test
    @DisplayName("parallel_tool_calls=false 时 ReActAgent 走串行 AbilityManager 执行")
    void executeSequentiallyWhenParallelDisabled() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(2).parallelToolCalls(false).build());
        CountDownLatch bothStarted = new CountDownLatch(2);
        String firstId = registerBlockingTool("serial-first", bothStarted, false);
        String secondId = registerBlockingTool("serial-second", bothStarted, false);

        List<AbilityManager.ExecutionResult> results = invokeExecuteToolCalls(
                List.of(
                        ToolCall.builder().id("tc-1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name(secondId).arguments("{}").build()
                ),
                AgentSession.createAgentSession("serial-ut-session", null, agent.getCard())
        );

        assertThat(results).hasSize(2);
        assertThat(String.valueOf(results.get(0).result())).contains("parallel=false");
        assertThat(String.valueOf(results.get(1).result())).contains("parallel=false");
    }

    @SuppressWarnings("unchecked")
    private List<AbilityManager.ExecutionResult> invokeExecuteToolCalls(
            List<ToolCall> toolCalls,
            AgentSession session
    ) throws Exception {
        Method method = ReActAgent.class.getDeclaredMethod(
                "executeToolCallsAndWriteToolMessages",
                AgentCallbackContext.class,
                List.class,
                com.openjiuwen.core.session.AgentSessionApi.class,
                ModelContext.class
        );
        method.setAccessible(true);
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setSession(session);
        ModelContext context = agent.initContext(session);
        ctx.setContext(context);
        return (List<AbilityManager.ExecutionResult>) method.invoke(agent, ctx, toolCalls, session, context);
    }

    private String registerBlockingTool(String prefix, CountDownLatch bothStarted) {
        return registerBlockingTool(prefix, bothStarted, true);
    }

    private String registerBlockingTool(String prefix, CountDownLatch bothStarted, boolean awaitPeer) {
        String toolId = prefix + "-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description(prefix).inputParams(Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of()
                )).build(),
                inputs -> {
                    bothStarted.countDown();
                    boolean parallel = awaitPeer && await(bothStarted);
                    return "name=" + toolId + ":parallel=" + parallel
                            + ":thread=" + Thread.currentThread().getName();
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        toolIds.add(toolId);
        agent.getAbilityManager().add(tool.getCard());
        return toolId;
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
