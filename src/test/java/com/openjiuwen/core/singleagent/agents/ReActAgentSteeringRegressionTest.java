// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.task_loop.LoopQueues;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression tests for {@link ReActAgent} steering queue provisioning and
 * main-loop steering continuation.
 */
class ReActAgentSteeringRegressionTest {
    private ReActAgent agent;

    private static final class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.builder().name("steering-regression-agent").description("Steering Regression").build();
        agent = new ReActAgent(card);
    }

    @AfterEach
    void tearDown() {
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    void stringInvokeBindsSteeringQueue() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        List<Boolean> queueBoundSeen = new ArrayList<>();
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("done").build());
        agent.setLlm(model);
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                queueBoundSeen.add(ctx.hasSteeringQueue());
            }
        });

        agent.invoke("do something", new TestSession("steering-string"));

        assertThat(queueBoundSeen)
                .as("String-invoke branch must auto-provision a steering queue")
                .isNotEmpty()
                .allMatch(bound -> bound);
    }

    @Test
    void stringInvokeSteeringReachesNextModelRequest() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        List<List<BaseMessage>> capturedRequests = new ArrayList<>();
        AtomicBoolean pushed = new AtomicBoolean(false);
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<BaseMessage> messages = (List<BaseMessage>) invocation.getArgument(0);
                    capturedRequests.add(new ArrayList<>(messages));
                    return AssistantMessage.builder().content("done").build();
                });
        agent.setLlm(model);
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("[STEERING] Continue");
                }
            }
        });

        agent.invoke("do something", new TestSession("steering-reach"));

        assertThat(capturedRequests)
                .as("steering must continue the loop so a second model request is issued")
                .hasSize(2);
        assertThat(secondRequestSteering(capturedRequests))
                .as("the second model request must carry the injected steering")
                .isTrue();
    }

    @Test
    void nullResponseContinuesOnPendingSteering() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        LoopQueues queues = new LoopQueues();
        List<List<BaseMessage>> capturedRequests = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicBoolean pushed = new AtomicBoolean(false);
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<BaseMessage> messages = (List<BaseMessage>) invocation.getArgument(0);
                    capturedRequests.add(new ArrayList<>(messages));
                    if (callCount.incrementAndGet() == 1) {
                        return null;
                    }
                    return AssistantMessage.builder().content("done").build();
                });
        agent.setLlm(model);
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("[STEERING] Continue");
                }
            }
        });

        Object result =
            agent.invoke(Map.of("query", "run", "loop_queues", queues), new TestSession("steering-null"));

        assertThat(capturedRequests)
                .as("null-response exit branch must continue on pending steering")
                .hasSize(2);
        assertThat(secondRequestSteering(capturedRequests))
                .as("the second model request must carry the injected steering")
                .isTrue();
        assertThat(((Map<?, ?>) result).get("output"))
                .as("loop must converge to the model's terminal answer")
                .isEqualTo("done");
        assertThat(queues.drainSteering())
                .as("steering must be consumed by the second iteration")
                .isEmpty();
    }

    @Test
    void answerBranchContinuesOnPendingSteering() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        LoopQueues queues = new LoopQueues();
        List<List<BaseMessage>> capturedRequests = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicBoolean pushed = new AtomicBoolean(false);
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<BaseMessage> messages = (List<BaseMessage>) invocation.getArgument(0);
                    capturedRequests.add(new ArrayList<>(messages));
                    if (callCount.incrementAndGet() == 1) {
                        return AssistantMessage.builder().content("intermediate").build();
                    }
                    return AssistantMessage.builder().content("done").build();
                });
        agent.setLlm(model);
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                if (pushed.compareAndSet(false, true)) {
                    ctx.pushSteering("[STEERING] Continue");
                }
            }
        });

        Object result =
            agent.invoke(Map.of("query", "run", "loop_queues", queues), new TestSession("steering-answer"));

        assertThat(capturedRequests)
                .as("answer exit branch must continue on pending steering")
                .hasSize(2);
        assertThat(secondRequestSteering(capturedRequests))
                .as("the second model request must carry the injected steering")
                .isTrue();
        assertThat(((Map<?, ?>) result).get("output"))
                .as("loop must converge to the model's terminal answer")
                .isEqualTo("done");
        assertThat(queues.drainSteering())
                .as("steering must be consumed by the second iteration")
                .isEmpty();
    }

    @Test
    void mapInvokeBindsSteeringQueue() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        LoopQueues queues = new LoopQueues();
        List<Boolean> queueBoundSeen = new ArrayList<>();
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("done").build());
        agent.setLlm(model);
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                queueBoundSeen.add(ctx.hasSteeringQueue());
            }
        });

        agent.invoke(Map.of("query", "run", "loop_queues", queues), new TestSession("steering-map"));

        assertThat(queueBoundSeen)
                .as("Map-invoke branch with loop_queues must bind the steering queue (control case)")
                .isNotEmpty()
                .allMatch(bound -> bound);
    }

    @Test
    void pushSteeringWarnsWhenNoQueueBound() {
        Logger agentLogger = (Logger) LoggerFactory.getLogger("agent");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        agentLogger.addAppender(appender);
        try {
            AgentCallbackContext ctx = AgentCallbackContext.builder().build();
            ctx.pushSteering("orphan steering");

            List<String> messages =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages)
                .as("pushSteering must warn when no steering queue is bound")
                .anyMatch(message -> message.contains("no steering queue bound"));
        } finally {
            agentLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private static boolean secondRequestSteering(List<List<BaseMessage>> capturedRequests) {
        if (capturedRequests.size() < 2) {
            return false;
        }
        for (BaseMessage message : capturedRequests.get(1)) {
            if (String.valueOf(message.getContent()).contains("[STEERING]")) {
                return true;
            }
        }
        return false;
    }
}
