/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.AgentTerminationReason;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for request-local ReAct iteration and termination observation.
 */
class ReActAgentLifecycleStateTest {
    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ReActAgent(AgentCard.builder().name("lifecycle-state-agent")
                .description("Lifecycle state agent").build());
    }

    @AfterEach
    void tearDown() {
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    void invokeExposesZeroBasedIterationAndTextTermination() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("done").build());
        agent.setLlm(model);
        List<LoopSnapshot> modelSnapshots = new ArrayList<>();
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                ctx -> modelSnapshots.add(snapshot(ctx)), 50);
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> terminal.set(snapshot(ctx)), 50);

        agent.invoke("run", new TestSession("invoke-text"));

        assertThat(modelSnapshots).containsExactly(
                new LoopSnapshot(0, 3, 2, null));
        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 3, 2, AgentTerminationReason.TEXT_TERMINATION));
    }

    @Test
    void streamExposesSameLifecycleSemantics() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(AssistantMessageChunk.builder().content("done").build()).iterator());
        agent.setLlm(model);
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> terminal.set(snapshot(ctx)), 50);
        AgentSessionApi session = new AgentSessionApi("stream-text", null, agent.getCard(),
                List.of(StreamMode.OUTPUT));

        agent.stream(Map.of("query", "run"), session, List.of(StreamMode.OUTPUT)).forEachRemaining(ignored -> {
        });

        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 2, 1, AgentTerminationReason.TEXT_TERMINATION));
    }

    @Test
    void streamFallbackModelFailureUsesModelErrorReason() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(2).streamMaxRetries(0).build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator());
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("fallback unavailable"));
        agent.setLlm(model);
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> terminal.set(snapshot(ctx)), 50);
        AgentSessionApi session = new AgentSessionApi("stream-failed", null, agent.getCard(),
                List.of(StreamMode.OUTPUT));

        agent.stream(Map.of("query", "run"), session, List.of(StreamMode.OUTPUT)).forEachRemaining(ignored -> {
        });

        assertThat(terminal.get().terminationReason()).isEqualTo(AgentTerminationReason.MODEL_ERROR);
    }

    @Test
    void maxIterationAndModelFailureHaveDistinctReasons() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        Model maxedModel = mock(Model.class);
        when(maxedModel.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().toolCalls(List.of(
                        ToolCall.builder().id("call-limit").name("unused").arguments("{}").build())).build());
        agent.setLlm(maxedModel);
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> terminal.set(snapshot(ctx)), 50);

        agent.invoke("run", new TestSession("maxed"));

        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 1, 0, AgentTerminationReason.MAX_ITERATIONS));

        Model failingModel = mock(Model.class);
        when(failingModel.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("model unavailable"));
        agent.setLlm(failingModel);

        assertThatThrownBy(() -> agent.invoke("run", new TestSession("failed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model unavailable");
        assertThat(terminal.get().terminationReason()).isEqualTo(AgentTerminationReason.MODEL_ERROR);
    }

    @Test
    void nonModelFailureUsesGenericErrorReason() {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> terminal.set(snapshot(ctx)), 50);

        assertThatThrownBy(() -> agent.invoke("", new TestSession("invalid-input")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input must contain 'query'");

        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(-1, 3, 3, AgentTerminationReason.ERROR));
    }

    @Test
    void concurrentSessionsKeepLifecycleStateIsolated() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        CountDownLatch firstCallsStarted = new CountDownLatch(2);
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<BaseMessage> messages = (List<BaseMessage>) invocation.getArgument(0);
                    boolean isFirstCall = messages.stream()
                            .noneMatch(message -> String.valueOf(message.getContent()).contains("[STEERING]"));
                    if (isFirstCall) {
                        firstCallsStarted.countDown();
                        assertThat(firstCallsStarted.await(3, TimeUnit.SECONDS)).isTrue();
                    }
                    return AssistantMessage.builder().content(isFirstCall ? "intermediate" : "done").build();
                });
        agent.setLlm(model);
        Set<String> steeredSessions = ConcurrentHashMap.newKeySet();
        Map<String, LoopSnapshot> terminalBySession = new ConcurrentHashMap<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                if ("session-a".equals(ctx.getSession().getSessionId())
                        && steeredSessions.add(ctx.getSession().getSessionId())) {
                    ctx.pushSteering("continue");
                }
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                terminalBySession.put(ctx.getSession().getSessionId(), snapshot(ctx));
            }
        });

        ExecutorService executor = newFixedExecutor(2);
        try {
            Future<?> first = executor.submit(() -> agent.invoke("run-a", new TestSession("session-a")));
            Future<?> second = executor.submit(() -> agent.invoke("run-b", new TestSession("session-b")));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(terminalBySession).containsEntry("session-a",
                new LoopSnapshot(1, 3, 1, AgentTerminationReason.TEXT_TERMINATION));
        assertThat(terminalBySession).containsEntry("session-b",
                new LoopSnapshot(0, 3, 2, AgentTerminationReason.TEXT_TERMINATION));
    }

    private static LoopSnapshot snapshot(AgentCallbackContext ctx) {
        return new LoopSnapshot(ctx.getIteration(), ctx.getMaxIterations(), ctx.getRemainingIterations(),
                ctx.getTerminationReason());
    }

    private static ExecutorService newFixedExecutor(int size) {
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(size));
    }

    private record LoopSnapshot(int iteration, int maxIterations, int remainingIterations,
            AgentTerminationReason terminationReason) {
    }

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
}
