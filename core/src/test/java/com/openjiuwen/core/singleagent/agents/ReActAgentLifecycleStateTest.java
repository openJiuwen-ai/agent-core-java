/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Uninterruptibles;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSession;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
        agent.getAgentCallbackManager().clear(null).toCompletableFuture().join();
    }

    @Test
    void invokeExposesZeroBasedIterationAndTextTermination() {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        agent.setLlm(answeringModel("done"));
        List<LoopSnapshot> modelSnapshots = new ArrayList<>();
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
            modelSnapshots.add(snapshot(ctx));
            return CompletableFuture.completedFuture(null);
        }, 50).toCompletableFuture().join();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> {
            terminal.set(snapshot(ctx));
            return CompletableFuture.completedFuture(null);
        }, 50).toCompletableFuture().join();

        agent.invoke("run", session("invoke-text"));

        assertThat(modelSnapshots).containsExactly(new LoopSnapshot(0, 3, 2, null));
        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 3, 2, AgentTerminationReason.TEXT_TERMINATION));
    }

    @Test
    void streamExposesSameLifecycleState() {
        agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
        agent.setLlm(streamingModel("done"));
        AtomicReference<LoopSnapshot> terminal = captureTerminalState();
        List<Object> outputs = new ArrayList<>();

        agent.stream(Map.of("query", "run"), session("stream-text"), List.of(StreamMode.OUTPUT))
                .forEachRemaining(outputs::add);

        assertThat(outputs).isNotEmpty();
        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 2, 1, AgentTerminationReason.TEXT_TERMINATION));
    }

    @Test
    void forceFinishExposesPreLoopState() {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        AtomicReference<LoopSnapshot> terminal = captureTerminalState();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                ctx.requestForceFinish(Map.of("output", "done", "result_type", "answer"));
            }
        }).toCompletableFuture().join();

        agent.invoke("run", session("force-finish"));

        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(-1, 3, 3, AgentTerminationReason.FORCE_FINISH));
    }

    @Test
    void forceFinishBeforeModelCallKeepsItsTerminationReason() {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        AtomicReference<LoopSnapshot> terminal = captureTerminalState();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(Map.of("output", "blocked", "result_type", "answer"));
            }
        }).toCompletableFuture().join();

        Object result = agent.invoke("run", session("force-before-model"));

        assertThat(result).isEqualTo(Map.of("output", "blocked", "result_type", "answer"));
        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 3, 2, AgentTerminationReason.FORCE_FINISH));
    }

    @Test
    void maxIterationAndModelFailureExposeDistinctReasons() {
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        agent.setLlm(new Model((messages, options) -> CompletableFuture.completedFuture(
                AssistantMessage.builder().toolCalls(List.of(ToolCall.builder()
                        .id("call-limit").name("missing-tool").arguments("{}").build())).build())));
        AtomicReference<LoopSnapshot> terminal = captureTerminalState();

        agent.invoke("run", session("maxed"));
        assertThat(terminal.get()).isEqualTo(
                new LoopSnapshot(0, 1, 0, AgentTerminationReason.MAX_ITERATIONS));

        agent.setLlm(new Model((messages, options) ->
                CompletableFuture.failedFuture(new IllegalStateException("model unavailable"))));
        assertThatThrownBy(() -> agent.invoke("run", session("failed")))
                .hasRootCauseMessage("model unavailable");
        assertThat(terminal.get().terminationReason()).isEqualTo(AgentTerminationReason.MODEL_ERROR);
    }

    @Test
    void concurrentSessionsKeepLifecycleStateIsolated() throws Exception {
        agent.configure(ReActAgentConfig.builder().maxIterations(3).build());
        CountDownLatch firstCallsStarted = new CountDownLatch(2);
        agent.setLlm(concurrentModel(firstCallsStarted));
        Set<String> steeredSessions = ConcurrentHashMap.newKeySet();
        Map<String, LoopSnapshot> terminals = new ConcurrentHashMap<>();
        agent.registerRail(sessionTrackingRail(steeredSessions, terminals)).toCompletableFuture().join();

        ExecutorService executor = fixedExecutor(2);
        try {
            Future<?> first = executor.submit(() -> agent.invoke("run-a", session("session-a")));
            Future<?> second = executor.submit(() -> agent.invoke("run-b", session("session-b")));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(terminals).containsEntry("session-a",
                new LoopSnapshot(1, 3, 1, AgentTerminationReason.TEXT_TERMINATION));
        assertThat(terminals).containsEntry("session-b",
                new LoopSnapshot(0, 3, 2, AgentTerminationReason.TEXT_TERMINATION));
    }

    private AtomicReference<LoopSnapshot> captureTerminalState() {
        AtomicReference<LoopSnapshot> terminal = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> {
            terminal.set(snapshot(ctx));
            return CompletableFuture.completedFuture(null);
        }, 50).toCompletableFuture().join();
        return terminal;
    }

    private static Model answeringModel(String content) {
        return new Model((messages, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static Model streamingModel(String content) {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(new AssistantMessage(content));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.of(AssistantMessageChunk.builder().content(content).build()).iterator();
            }
        });
    }

    private static Model concurrentModel(CountDownLatch firstCallsStarted) {
        return new Model((messages, options) -> {
            boolean isFirstCall = messages.stream()
                    .noneMatch(message -> String.valueOf(message.getContent()).contains("[STEERING]"));
            if (isFirstCall) {
                firstCallsStarted.countDown();
                await(firstCallsStarted);
            }
            return CompletableFuture.completedFuture(new AssistantMessage(isFirstCall ? "intermediate" : "done"));
        });
    }

    private static AgentRail sessionTrackingRail(Set<String> steeredSessions,
                                                 Map<String, LoopSnapshot> terminals) {
        return new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                String sessionId = ctx.getSession().getSessionId();
                if ("session-a".equals(sessionId) && steeredSessions.add(sessionId)) {
                    ctx.pushSteering("continue");
                }
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                terminals.put(ctx.getSession().getSessionId(), snapshot(ctx));
            }
        };
    }

    private static void await(CountDownLatch latch) {
        assertThat(Uninterruptibles.awaitUninterruptibly(latch, 3, TimeUnit.SECONDS)).isTrue();
    }

    private static LoopSnapshot snapshot(AgentCallbackContext ctx) {
        return new LoopSnapshot(ctx.getIteration(), ctx.getMaxIterations(), ctx.getRemainingIterations(),
                ctx.getTerminationReason());
    }

    private static AgentSession session(String sessionId) {
        return new AgentSession(sessionId, null, null);
    }

    private static ExecutorService fixedExecutor(int size) {
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(size));
    }

    private record LoopSnapshot(int iteration, int maxIterations, int remainingIterations,
                                AgentTerminationReason terminationReason) {
    }
}
