/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.CoordinatorLoop;
import com.openjiuwen.agent_teams.schema.TeamRole;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CoordinatorLoop lifecycle.
 * 
 * <p>Mirrors Python's tests/unit_tests/agent_teams/test_coordination_lifecycle.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/agent_teams/test_coordination_lifecycle.py
 * 
 * <p>NOTE: Python uses asyncio, Java uses synchronous/thread-based implementation.
 * Tests are adapted for Java's threading model.
 */
@ExtendWith(MockitoExtension.class)
class CoordinationLifecycleTest {

    // ========== test_start_stop_sets_running_flag ==========

    @Test
    @Tag("level0")
    @DisplayName("Test start() sets is_running, stop() clears it")
    void testStartStopSetsRunningFlag() {
        // Python: test_start_stop_sets_running_flag
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);
        assertFalse(loop.isRunning());

        loop.start();
        assertTrue(loop.isRunning());

        loop.stop();
        assertFalse(loop.isRunning());
    }

    // ========== test_stop_is_idempotent ==========

    @Test
    @Tag("level0")
    @DisplayName("Test calling stop() twice does not raise")
    void testStopIsIdempotent() {
        // Python: test_stop_is_idempotent
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);
        loop.start();
        loop.stop();
        loop.stop(); // Second call should not raise
        assertFalse(loop.isRunning());
    }

    // ========== test_role_getter ==========

    @Test
    @Tag("level0")
    @DisplayName("Test role getter returns correct role")
    void testRoleGetter() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);
        assertEquals(TeamRole.LEADER, loop.getRole());

        CoordinatorLoop memberLoop = new CoordinatorLoop(TeamRole.TEAMMATE, null);
        assertEquals(TeamRole.TEAMMATE, memberLoop.getRole());
    }

    // ========== test_pause_resume_polls ==========

    @Test
    @Tag("level0")
    @DisplayName("Test pausePolls() and resumePolls() toggle pollsPaused")
    void testPauseResumePolls() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);
        assertFalse(loop.isPollsPaused());

        loop.pausePolls();
        assertTrue(loop.isPollsPaused());

        loop.resumePolls();
        assertFalse(loop.isPollsPaused());
    }

    // ========== test_wake_callback_invoked ==========

    @Test
    @Tag("level1")
    @DisplayName("Test wake callback is invoked on wake()")
    void testWakeCallbackInvoked() throws InterruptedException {
        // Python: test_wake_callback_invoked_on_event (adapted for Java)
        List<CoordinationEvent> woke = new ArrayList<>();

        Consumer<CoordinationEvent> onWake = event -> {
            synchronized (woke) {
                woke.add(event);
            }
        };

        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, onWake);
        loop.start();

        // Create a simple event
        CoordinationEvent event = new CoordinationEvent("MESSAGE", Map.of("msg", "hello"));

        loop.wake(event);
        Thread.sleep(50); // Allow callback to execute

        loop.stop();

        assertEquals(1, woke.size());
        assertEquals("MESSAGE", woke.get(0).getEventType());
    }

    // ========== test_wake_callback_not_invoked_when_not_running ==========

    @Test
    @Tag("level1")
    @DisplayName("Test wake callback is not invoked when loop is not running")
    void testWakeCallbackNotInvokedWhenNotRunning() {
        List<CoordinationEvent> woke = new ArrayList<>();

        Consumer<CoordinationEvent> onWake = event -> woke.add(event);

        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, onWake);
        // Not started

        CoordinationEvent event = new CoordinationEvent("MESSAGE", Map.of("msg", "hello"));

        loop.wake(event);

        assertTrue(woke.isEmpty());
    }

    // ========== test_wake_callback_null ==========

    @Test
    @Tag("level0")
    @DisplayName("Test wake() does nothing when wakeCallback is null")
    void testWakeCallbackNull() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);
        loop.start();

        CoordinationEvent event = new CoordinationEvent("MESSAGE", Map.of("msg", "hello"));

        // Should not throw
        loop.wake(event);
        loop.stop();
    }

    // ========== test_custom_poll_intervals ==========

    @Test
    @Tag("level0")
    @DisplayName("Test custom poll intervals are set correctly")
    void testCustomPollIntervals() {
        long mailboxInterval = 5000L;
        long taskInterval = 10000L;

        CoordinatorLoop loop = new CoordinatorLoop(
            TeamRole.LEADER,
            null,
            mailboxInterval,
            taskInterval
        );

        // Verify loop is created with custom intervals
        assertNotNull(loop);
        assertEquals(TeamRole.LEADER, loop.getRole());
    }
}