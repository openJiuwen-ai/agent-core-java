/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.coordination.EventBus;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_coordination_loop} in
 * {@code tests/unit_tests/agent_teams/test_coordination_loop.py}.
 */
class EventBusCoordinationLoopPythonParityTest {

    @Test
    void testMessageEventWakesLoop() throws InterruptedException {
        List<CoordinationEvent> woke = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EventBus loop = new EventBus(TeamRole.LEADER)) {
            loop.start(event -> {
                woke.add(event);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            loop.enqueue(new EventMessage(TeamEvent.MESSAGE, Map.of("content", "hello"), ""))
                    .toCompletableFuture().join();

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(woke).hasSize(1);
            assertThat(woke.get(0).eventKey()).isEqualTo(TeamEvent.MESSAGE);
        }
    }

    @Test
    void testTaskEventWakesLoop() throws InterruptedException {
        List<CoordinationEvent> woke = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (EventBus loop = new EventBus(TeamRole.TEAMMATE)) {
            loop.start(event -> {
                woke.add(event);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            loop.enqueue(new EventMessage(TeamEvent.TASK_COMPLETED, Map.of("task_id", "t1"), ""))
                    .toCompletableFuture().join();

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(woke).hasSize(1);
            assertThat(woke.get(0).eventKey()).isEqualTo(TeamEvent.TASK_COMPLETED);
        }
    }

    @Test
    void testMultipleEventsWakeInOrder() throws InterruptedException {
        List<String> woke = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        try (EventBus loop = new EventBus(TeamRole.LEADER)) {
            loop.start(event -> {
                woke.add(event.eventKey());
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            for (String eventType : List.of(TeamEvent.MESSAGE, TeamEvent.TASK_COMPLETED, TeamEvent.BROADCAST)) {
                loop.enqueue(new EventMessage(eventType, Map.of(), "")).toCompletableFuture().join();
            }

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(woke).containsExactly(TeamEvent.MESSAGE, TeamEvent.TASK_COMPLETED, TeamEvent.BROADCAST);
        }
    }

    @Test
    void testNoCallbackDoesNotCrash() throws InterruptedException {
        try (EventBus loop = new EventBus(TeamRole.LEADER)) {
            loop.start().toCompletableFuture().join();
            loop.enqueue(new EventMessage(TeamEvent.MESSAGE, Map.of(), "")).toCompletableFuture().join();
            TimeUnit.MILLISECONDS.sleep(50L);
            assertThat(loop.isRunning()).isTrue();
        }
    }

    @Test
    void testHumanAgentBusDoesNotStartPollTimers() {
        try (EventBus loop = new EventBus(TeamRole.HUMAN_AGENT)) {
            loop.start().toCompletableFuture().join();

            assertThat(loop.isRunning()).isTrue();
            assertThat(loop.hasMailboxPollTask()).isFalse();
            assertThat(loop.hasTaskPollTask()).isFalse();
        }
    }

    @Test
    void testNonHumanBusStartsPollTimers() {
        for (TeamRole role : List.of(TeamRole.LEADER, TeamRole.TEAMMATE)) {
            try (EventBus loop = new EventBus(role)) {
                loop.start().toCompletableFuture().join();

                assertThat(loop.hasMailboxPollTask()).isTrue();
                assertThat(loop.hasTaskPollTask()).isTrue();
            }
        }
    }

    @Test
    void testHumanAgentResumePollsStaysNoop() {
        try (EventBus loop = new EventBus(TeamRole.HUMAN_AGENT)) {
            loop.start().toCompletableFuture().join();
            loop.pausePolls().toCompletableFuture().join();
            assertThat(loop.isPollsPaused()).isTrue();

            loop.resumePolls().toCompletableFuture().join();

            assertThat(loop.isPollsPaused()).isFalse();
            assertThat(loop.hasMailboxPollTask()).isFalse();
            assertThat(loop.hasTaskPollTask()).isFalse();
        }
    }
}
