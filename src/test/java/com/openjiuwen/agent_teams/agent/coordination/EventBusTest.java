/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link EventBus}.
 *
 * <p>Mirrors Python's queue, lifecycle, poll pause/resume, and wake callback
 * behavior in {@code openjiuwen/agent_teams/agent/coordination/event_bus.py}.</p>
 */
class EventBusTest {

    @Test
    void startBindsWakeCallbackAndStopResetsRunningState() {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        try {
            bus.start(event -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();

            assertTrue(bus.isRunning());
            assertTrue(bus.hasMailboxPollTask());
            assertTrue(bus.hasTaskPollTask());

            bus.stop().toCompletableFuture().join();

            assertFalse(bus.isRunning());
            assertFalse(bus.isPollsPaused());
            assertFalse(bus.hasMailboxPollTask());
            assertFalse(bus.hasTaskPollTask());
        } finally {
            bus.close();
        }
    }

    @Test
    void humanAgentNeverStartsPeriodicPollTasks() {
        EventBus bus = new EventBus(TeamRole.HUMAN_AGENT, 0.01, 0.01);
        try {
            bus.start(event -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();

            assertFalse(bus.isPeriodicPollEnabled());
            assertFalse(bus.hasMailboxPollTask());
            assertFalse(bus.hasTaskPollTask());
        } finally {
            bus.close();
        }
    }

    @Test
    void pauseAndResumePollsFollowPythonStateMachine() {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        try {
            bus.start(event -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();
            bus.pausePolls().toCompletableFuture().join();

            assertTrue(bus.isPollsPaused());
            assertFalse(bus.hasMailboxPollTask());
            assertFalse(bus.hasTaskPollTask());

            bus.resumePolls().toCompletableFuture().join();

            assertFalse(bus.isPollsPaused());
            assertTrue(bus.hasMailboxPollTask());
            assertTrue(bus.hasTaskPollTask());
        } finally {
            bus.close();
        }
    }

    @Test
    void enqueueInnerEventInvokesWakeCallback() throws InterruptedException {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        CountDownLatch latch = new CountDownLatch(1);
        List<CoordinationEvent> received = new ArrayList<>();
        try {
            bus.start(event -> {
                received.add(event);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            bus.enqueue(new InnerEventMessage(InnerEventType.USER_INPUT)).toCompletableFuture().join();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertInstanceOf(InnerEventMessage.class, received.getFirst());
            assertEquals("user_input", received.getFirst().eventKey());
        } finally {
            bus.close();
        }
    }

    @Test
    void enqueueTransportEventWrapsEventMessageForCallback() throws InterruptedException {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        CountDownLatch latch = new CountDownLatch(1);
        List<CoordinationEvent> received = new ArrayList<>();
        try {
            bus.start(event -> {
                received.add(event);
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            bus.enqueue(new EventMessage(TeamEvent.MESSAGE, null, "sender")).toCompletableFuture().join();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertInstanceOf(TransportEvent.class, received.getFirst());
            assertEquals(TeamEvent.MESSAGE, received.getFirst().eventKey());
        } finally {
            bus.close();
        }
    }

    @Test
    void wakeCallbackExceptionIsLoggedAndLoopContinues() throws InterruptedException {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> keys = new ArrayList<>();
        try {
            bus.start(event -> {
                keys.add(event.eventKey());
                latch.countDown();
                if (keys.size() == 1) {
                    throw new IllegalStateException("boom");
                }
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            bus.enqueue(new InnerEventMessage(InnerEventType.USER_INPUT)).toCompletableFuture().join();
            bus.enqueue(new InnerEventMessage(InnerEventType.POLL_TASK)).toCompletableFuture().join();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(bus.isRunning());
            assertEquals(List.of("user_input", "coordination_poll_task"), keys);
        } finally {
            bus.close();
        }
    }

    @Test
    void periodicPollTasksEnqueueMailboxAndTaskEvents() throws InterruptedException {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 0.01, 0.01);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> keys = new ArrayList<>();
        try {
            bus.start(event -> {
                keys.add(event.eventKey());
                if (keys.contains("coordination_poll_mailbox") && keys.contains("coordination_poll_task")) {
                    latch.countDown();
                    latch.countDown();
                }
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(keys.contains("coordination_poll_mailbox"));
            assertTrue(keys.contains("coordination_poll_task"));
        } finally {
            bus.close();
        }
    }

    @Test
    void startWithNullCallbackKeepsPreviouslyBoundCallback() throws InterruptedException {
        EventBus bus = new EventBus(TeamRole.TEAMMATE, 30.0, 30.0);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            bus.start(event -> {
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();
            bus.stop().toCompletableFuture().join();
            bus.start().toCompletableFuture().join();

            bus.enqueue(new InnerEventMessage(InnerEventType.USER_INPUT)).toCompletableFuture().join();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } finally {
            bus.close();
        }
    }
}
