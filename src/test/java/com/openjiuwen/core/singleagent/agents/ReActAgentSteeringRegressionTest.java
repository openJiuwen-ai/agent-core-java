/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: String invoke and empty-response exit must keep steering usable.
 */
class ReActAgentSteeringRegressionTest {
    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.builder()
                .name("steering-regression-agent")
                .description("Steering Regression")
                .build();
        agent = new ReActAgent(card);
    }

    @AfterEach
    void tearDown() {
        if (agent != null) {
            agent.getAgentCallbackManager().clear(null).toCompletableFuture().join();
        }
    }

    @Test
    void stringInvokeBindsSteeringQueue() {
        agent.configure(new ReActAgentConfig().configureMaxIterations(1));
        List<Boolean> queueBoundSeen = new ArrayList<>();
        agent.setLlm(new Model((messages, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("done"))));
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> afterModelCall(AgentCallbackContext ctx) {
                queueBoundSeen.add(ctx.hasSteeringQueue());
                return completed();
            }
        }).toCompletableFuture().join();

        agent.invoke("do something", new AgentSession("steering-string", null, agent.getCard()))
                .toCompletableFuture()
                .join();

        assertThat(queueBoundSeen)
                .as("String-invoke branch must auto-provision a steering queue")
                .isNotEmpty()
                .allMatch(bound -> bound);
    }

    @Test
    void stringInvokeSteeringReachesNextModelRequest() {
        agent.configure(new ReActAgentConfig().configureMaxIterations(3));
        List<List<BaseMessage>> capturedRequests = new ArrayList<>();
        AtomicBoolean pushed = new AtomicBoolean(false);
        agent.setLlm(new Model((messages, options) -> {
            capturedRequests.add(new ArrayList<>(messages));
            return CompletableFuture.completedFuture(new AssistantMessage("done"));
        }));
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("[STEERING] Continue");
                }
                return completed();
            }
        }).toCompletableFuture().join();

        agent.invoke("do something", new AgentSession("steering-continue", null, agent.getCard()))
                .toCompletableFuture()
                .join();

        assertThat(capturedRequests.size()).isGreaterThanOrEqualTo(2);
        assertThat(capturedRequests.get(1).stream().map(BaseMessage::getContent).map(String::valueOf))
                .anyMatch(content -> content.contains("[STEERING] Continue"));
    }
}
