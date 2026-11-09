/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.core.common.logging.Loggers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Keeps failed member execution observable without losing submission or cleanup exception ownership.
 *
 * @since 2026-11-09
 */
@Isolated("Captures the shared agent logger and scoped TeamAgent construction")
@Timeout(20)
class InProcessSpawnFailureTest {
    @Test
    void memberFailureIsLoggedOnceAndRemainsInTheFuture() throws InterruptedException {
        String member = "failure-" + UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("fixture execution failure");
        TeamAgent parent = parent();
        ExecutorService executor = inlineExecutor();
        try (FailureCapture logs = new FailureCapture(member);
                MockedConstruction<TeamAgent> construction = mockConstruction(TeamAgent.class, (agent, context) -> {
                    when(agent.configure(any(), any(), any())).thenReturn(agent);
                    doThrow(failure).when(agent).invokeForSpawn(any());
                })) {
            SpawnContext.SessionToken previous = SpawnContext.setSessionId("outer-session");
            try {
                InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(parent, context(member), executor,
                        "fixture query", "member-session");

                assertThatThrownBy(() -> handle.getTask().get()).isInstanceOf(ExecutionException.class)
                        .hasCause(failure);
                assertThat(handle.isHealthy()).isFalse();
                assertThat(handle.waitForCompletion()).isEqualTo(-1);
                assertThat(logs.failures).containsExactly(failure);
                assertThat(SpawnContext.getSessionId()).isEqualTo("outer-session");
                assertThat(construction.constructed()).hasSize(1);
            } finally {
                SpawnContext.resetSessionId(previous);
            }
        }
    }

    @Test
    void submissionFailureRetainsCleanupAsSuppressed() {
        IllegalStateException submission = new IllegalStateException("fixture executor failure");
        IllegalStateException cleanup = new IllegalStateException("fixture cleanup failure");
        TeamAgent parent = parent();
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(submission).when(executor).execute(any());
        try (MockedConstruction<TeamAgent> construction = mockConstruction(TeamAgent.class, (agent, context) -> {
            when(agent.configure(any(), any(), any())).thenReturn(agent);
            doThrow(cleanup).when(agent).close();
        })) {
            assertThatThrownBy(() -> InProcessSpawn.inprocessSpawn(parent, context("worker"), executor, "", "session"))
                    .isSameAs(submission).hasSuppressedException(cleanup);
            assertThat(construction.constructed()).hasSize(1);
        }
    }

    @Test
    void executionFailureRetainsCleanupAndRestoresSession() throws InterruptedException {
        String member = "cleanup-" + UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("fixture member failure");
        IllegalStateException cleanup = new IllegalStateException("fixture cleanup failure");
        TeamAgent parent = parent();
        try (FailureCapture logs = new FailureCapture(member);
                MockedConstruction<TeamAgent> construction = mockConstruction(TeamAgent.class, (agent, context) -> {
                    when(agent.configure(any(), any(), any())).thenReturn(agent);
                    doThrow(failure).when(agent).invokeForSpawn(any());
                    doThrow(cleanup).doNothing().when(agent).close();
                })) {
            SpawnContext.SessionToken previous = SpawnContext.setSessionId("outer-session");
            try {
                InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(parent, context(member), inlineExecutor(),
                        "fixture query", "member-session");
                assertThatThrownBy(() -> handle.getTask().get()).hasCause(failure);
                assertThat(failure.getSuppressed()).containsExactly(cleanup);
                assertThat(logs.failures).containsExactly(failure);
                assertThat(SpawnContext.getSessionId()).isEqualTo("outer-session");
                assertThat(construction.constructed()).hasSize(1);
            } finally {
                SpawnContext.resetSessionId(previous);
            }
        }
    }

    @Test
    void cancellationBeforeStartDoesNotLogAnExecutionFailure() {
        String member = "cancel-" + UUID.randomUUID();
        TeamAgent parent = parent();
        ExecutorService executor = mock(ExecutorService.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation -> {
            queued.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any());
        try (FailureCapture logs = new FailureCapture(member);
                MockedConstruction<TeamAgent> construction = mockConstruction(TeamAgent.class, (agent, context) ->
                        when(agent.configure(any(), any(), any())).thenReturn(agent))) {
            InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(parent, context(member), executor,
                    "", "member-session");
            assertThat(handle.getTask().cancel(false)).isTrue();
            queued.get().run();
            assertThat(handle.getTask()).isCancelled();
            assertThat(logs.failures).isEmpty();
            assertThat(construction.constructed()).hasSize(1);
        }
    }

    @Test
    void executorFailureAfterStartingTheTaskCancelsAndClosesTheMember() throws Exception {
        String member = "started-" + UUID.randomUUID();
        IllegalStateException submission = new IllegalStateException("fixture post-submission failure");
        CompletableFuture<Void> entered = new CompletableFuture<>();
        CompletableFuture<Void> closed = new CompletableFuture<>();
        CountDownLatch blocked = new CountDownLatch(1);
        TeamAgent parent = parent();
        ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), task -> new Thread(task, "spawn-handoff-fixture"));
        ExecutorService failing = mock(ExecutorService.class);
        doAnswer(invocation -> {
            worker.execute(invocation.getArgument(0));
            entered.get(3, TimeUnit.SECONDS);
            throw submission;
        }).when(failing).execute(any());
        try (FailureCapture logs = new FailureCapture(member);
                MockedConstruction<TeamAgent> construction = mockConstruction(TeamAgent.class, (agent, context) -> {
                    when(agent.configure(any(), any(), any())).thenReturn(agent);
                    doAnswer(invocation -> {
                        entered.complete(null);
                        blocked.await();
                        return null;
                    }).when(agent).invokeForSpawn(any());
                    doAnswer(invocation -> {
                        closed.complete(null);
                        return null;
                    }).when(agent).close();
                })) {
            assertThatThrownBy(() -> InProcessSpawn.inprocessSpawn(parent, context(member), failing,
                    "", "member-session")).isSameAs(submission);
            closed.get(3, TimeUnit.SECONDS);
            assertThat(logs.failures).isEmpty();
            assertThat(construction.constructed()).hasSize(1);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static TeamAgent parent() {
        TeamAgent parent = mock(TeamAgent.class);
        when(parent.getSpec()).thenReturn(TeamAgentSpec.builder().name("fixture-team").build());
        return parent;
    }

    private static TeamRuntimeContext context(String member) {
        return TeamRuntimeContext.builder().teamId("fixture-team").memberName(member).build();
    }

    private static ExecutorService inlineExecutor() {
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any());
        return executor;
    }

    private static final class FailureCapture extends Handler implements AutoCloseable {
        private final String member;

        private final List<Throwable> failures = new CopyOnWriteArrayList<>();

        private FailureCapture(String member) {
            this.member = member;
            Loggers.AGENT.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()
                    && record.getMessage().contains(member) && record.getThrown() != null) {
                failures.add(record.getThrown());
            }
        }

        @Override
        public void flush() {
            // Records are captured synchronously without an output buffer.
        }

        @Override
        public void close() {
            Loggers.AGENT.removeHandler(this);
        }
    }
}
