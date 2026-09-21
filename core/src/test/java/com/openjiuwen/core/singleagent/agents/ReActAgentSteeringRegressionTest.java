/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.task_loop.LoopQueues;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
            public void afterModelCall(AgentCallbackContext ctx) {
                queueBoundSeen.add(ctx.hasSteeringQueue());
                return;
            }
        }).toCompletableFuture().join();

        agent.invoke("do something", new AgentSession("steering-string", null, agent.getCard()));

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
            public void afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("[STEERING] Continue");
                }
                return;
            }
        }).toCompletableFuture().join();

        agent.invoke("do something", new AgentSession("steering-continue", null, agent.getCard()));

        assertThat(capturedRequests.size()).isGreaterThanOrEqualTo(2);
        assertThat(capturedRequests.get(1).stream().map(BaseMessage::getContent).map(String::valueOf))
                .anyMatch(content -> content.contains("[STEERING] Continue"));
    }

    @Test
    void mapInvokeBindsLoopQueuesSteering() {
        agent.configure(new ReActAgentConfig().configureMaxIterations(1));
        LoopQueues queues = new LoopQueues();
        List<Boolean> queueBoundSeen = new ArrayList<>();
        agent.setLlm(new Model((messages, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("done"))));
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                queueBoundSeen.add(ctx.hasSteeringQueue());
                ctx.pushSteering("live-steer");
                return;
            }
        }).toCompletableFuture().join();

        agent.invoke(
                Map.of("query", "run", "loop_queues", queues),
                new AgentSession("steering-map", null, agent.getCard())
        );

        assertThat(queueBoundSeen)
                .as("Map-invoke branch with loop_queues must bind the steering queue")
                .isNotEmpty()
                .allMatch(bound -> bound);
        assertThat(queues.steering()).contains("live-steer");
    }

    @Test
    void streamTextResponseContinuesOnPendingSteering() {
        agent.configure(new ReActAgentConfig().configureMaxIterations(3));
        List<List<BaseMessage>> capturedRequests = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger();
        AtomicBoolean pushed = new AtomicBoolean();
        agent.setLlm(streamingModel(capturedRequests, callCount));
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("continue");
                }
            }
        }).toCompletableFuture().join();
        AgentSession session = new AgentSession("steering-stream", null, agent.getCard());

        List<Object> outputs = new ArrayList<>();
        agent.stream(Map.of("query", "run"), session, List.of(StreamMode.OUTPUT))
                .forEachRemaining(outputs::add);

        assertThat(capturedRequests).hasSize(2);
        assertThat(capturedRequests.get(1).stream().map(BaseMessage::getContent).map(String::valueOf))
                .anyMatch(content -> content.contains("continue"));
        assertThat(outputs).anyMatch(output -> output instanceof OutputSchema schema
                && "answer".equals(schema.getType())
                && String.valueOf(schema.getPayload()).contains("done"));
    }

    private static Model streamingModel(List<List<BaseMessage>> capturedRequests, AtomicInteger callCount) {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(new AssistantMessage("fallback"));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                capturedRequests.add(new ArrayList<>(messages));
                String content = callCount.incrementAndGet() == 1 ? "intermediate" : "done";
                return List.of(AssistantMessageChunk.builder().content(content).build()).iterator();
            }
        });
    }
}
