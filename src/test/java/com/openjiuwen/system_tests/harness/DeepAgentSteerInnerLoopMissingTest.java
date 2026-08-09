/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.system_tests.harness.test_steer_inner_loop}
 * in {@code tests/system_tests/harness/test_steer_inner_loop.py}.
 */
class DeepAgentSteerInnerLoopMissingTest {

    @Test
    void testSteerVisibleInSameInvoke() throws Exception {
        String steerText = "please answer with concise Chinese bullets";
        AgentSession session = new AgentSession("steer_inner_" + uuid(), null, null);
        seedPlan(session);

        BlockingScriptedModel modelClient = new BlockingScriptedModel();
        ModelCallObserver observer = new ModelCallObserver();
        ReActAgent reactAgent = reactAgent(modelClient, observer);
        DeepAgent agent = deepAgent(reactAgent);

        CompletableFuture<Map<String, Object>> invokeTask = agent.invokeAsync(
                Map.of("query", "execute the two-step plan"),
                session
        );

        assertThat(modelClient.firstCallStarted.await(10, TimeUnit.SECONDS)).isTrue();
        agent.steerAsync(steerText, session).join();
        modelClient.releaseFirstCall.countDown();

        Map<String, Object> result = invokeTask.get(10, TimeUnit.SECONDS);

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(observer.modelCallMessages).hasSizeGreaterThanOrEqualTo(2);
        assertThat(observer.modelCallMessages.get(0))
                .noneMatch(message -> content(message).contains("[STEERING]"));
        assertThat(observer.modelCallMessages.get(1))
                .anyMatch(message -> {
                    String content = content(message);
                    return content.contains("[STEERING]") && content.contains(steerText);
                });
    }

    private static ReActAgent reactAgent(BlockingScriptedModel modelClient, ModelCallObserver observer) {
        ReActAgentConfig config = new ReActAgentConfig();
        config.setMaxIterations(3);
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "You are a test assistant.")));

        ReActAgent reactAgent = new ReActAgent(new AgentCard("react", "react", "test"));
        reactAgent.configure(config);
        reactAgent.setLlm(new Model(modelClient));
        reactAgent.getAbilityManager().add(new ToolCard(
                "blocking_tool",
                "blocking_tool",
                "A deterministic tool used to force a second model call."
        ));
        reactAgent.registerRail(observer).toCompletableFuture().join();
        return reactAgent;
    }

    private static DeepAgent deepAgent(ReActAgent reactAgent) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        config.setMaxIterations(3);

        DeepAgent agent = new DeepAgent(new AgentCard("deep_agent", "deep_agent", "test"));
        agent.configure(config);
        agent.setReactAgent(reactAgent, true);
        return agent;
    }

    private static void seedPlan(AgentSession session) {
        TaskPlan plan = new TaskPlan("test steering injection", List.of(new TodoItem("t1", "step-1")));
        session.updateState(Map.of("deepagent", Map.of("iteration", 0, "task_plan", plan.toMap())));
    }

    private static String content(Object message) {
        if (message instanceof BaseMessage baseMessage) {
            return baseMessage.getContentAsString();
        }
        if (message instanceof Map<?, ?> map) {
            return String.valueOf(map.get("content"));
        }
        return String.valueOf(message);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static final class ModelCallObserver extends AgentRail {
        private final List<List<Object>> modelCallMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
            if (context.getInputs() instanceof ModelCallInputs inputs) {
                modelCallMessages.add(new ArrayList<>(inputs.getMessages()));
            }
            return completed();
        }
    }

    private static final class BlockingScriptedModel implements Model.ModelClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private final CountDownLatch firstCallStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstCall = new CountDownLatch(1);

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            int callNo = callCount.incrementAndGet();
            if (callNo == 1) {
                firstCallStarted.countDown();
                awaitRelease();
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("tc_1")
                                .type("function")
                                .name("blocking_tool")
                                .arguments("{}")
                                .build()))
                        .finishReason("tool_calls")
                        .build());
            }
            return CompletableFuture.completedFuture(AssistantMessage.builder()
                    .content("first step completed")
                    .toolCalls(List.of())
                    .finishReason("stop")
                    .build());
        }

        private void awaitRelease() {
            try {
                if (!releaseFirstCall.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the first model call.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }
}
