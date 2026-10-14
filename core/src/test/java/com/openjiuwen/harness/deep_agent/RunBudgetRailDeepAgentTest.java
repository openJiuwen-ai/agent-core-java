/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.runbudget.RunBudgetConfig;
import com.openjiuwen.core.singleagent.runbudget.RunBudgetRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The run-budget rail is reconfigurable at DeepAgent creation time: the inner
 * ReActAgent exposed via {@code reactAgent()} accepts a rail carrying a fresh
 * {@link RunBudgetConfig}, and the rail then fires from the DeepAgent-driven loop.
 */
class RunBudgetRailDeepAgentTest {

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void runBudgetRailOnInnerReActAgentEmitsGuaranteeEvent() {
        // Given a DeepAgent whose inner ReActAgent carries a turn-budget rail
        AgentCard card = AgentCard.builder().id("run-budget-deep-001").description("budget").build();
        agent = new DeepAgent(card, DeepAgentConfig.builder().enableTaskLoop(true).build(), null);
        ReActAgent inner = (ReActAgent) agent.reactAgent();
        inner.registerRail(new RunBudgetRail(RunBudgetConfig.builder()
                .turnEnabled(true)
                .suggestedRounds(2)
                .build())).toCompletableFuture().join();
        inner.setLlm(stubModel());

        LoggerProtocol original = LogManager.getLogger("agent");
        LoggerProtocol recording = mock(LoggerProtocol.class);
        List<String> eventTypes = new ArrayList<>();
        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments.length >= 3 && arguments[arguments.length - 1] instanceof Map<?, ?>) {
                eventTypes.add(String.valueOf(arguments[arguments.length - 2]));
            }
            return null;
        }).when(recording).info(anyString(), Mockito.any(Object[].class));
        LogManager.registerLogger("agent", recording);
        try {
            // When invoked with a model that answers immediately
            Map<String, Object> result = agent.invoke(Map.of("query", "hi", "conversation_id", "rb-deep-001"));

            // Then the answer passed through and the guarantee event fired from the inner loop
            assertThat(result.get("output")).isEqualTo("done");
            assertThat(eventTypes).contains("turn_budget_guarantee_effective");
        } finally {
            LogManager.registerLogger("agent", original);
        }
    }

    @Test
    void runBudgetRailDeclaredInConfigRailsEmitsGuaranteeEvent() {
        // Given the budget rail declared at creation time via DeepAgentConfig.rails
        List<String> eventTypes = new ArrayList<>();
        AgentCard card = AgentCard.builder().id("run-budget-deep-002").description("budget").build();
        agent = new DeepAgent(card, DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .rails(List.of(new RunBudgetRail(RunBudgetConfig.builder()
                        .turnEnabled(true)
                        .suggestedRounds(2)
                        .build())))
                .build(), null);
        ((ReActAgent) agent.reactAgent()).setLlm(stubModel());

        LoggerProtocol original = LogManager.getLogger("agent");
        LoggerProtocol recording = mock(LoggerProtocol.class);
        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments.length >= 3 && arguments[arguments.length - 1] instanceof Map<?, ?>) {
                eventTypes.add(String.valueOf(arguments[arguments.length - 2]));
            }
            return null;
        }).when(recording).info(anyString(), Mockito.any(Object[].class));
        LogManager.registerLogger("agent", recording);
        try {
            // When invoked
            Map<String, Object> result = agent.invoke(Map.of("query", "hi", "conversation_id", "rb-deep-002"));

            // Then the creation-time rail is live in the loop
            assertThat(result.get("output")).isEqualTo("done");
            assertThat(eventTypes).contains("turn_budget_guarantee_effective");
        } finally {
            LogManager.registerLogger("agent", original);
        }
    }

    private static Model stubModel() {
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), anyBoolean())).thenReturn(Map.of());
        when(model.invoke(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        AssistantMessage.builder().content("done").toolCalls(List.of()).build()));
        return model;
    }
}
