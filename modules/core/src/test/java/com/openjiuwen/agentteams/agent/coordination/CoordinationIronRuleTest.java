/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.agent.coordination.handlers.BaseCoordinationHandler;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Iron-rule tests for the coordination subsystem.
 * Mirrors Python 0.1.15 coordination lifecycle + loop tests.
 */
class CoordinationIronRuleTest {

    @AfterEach
    void cleanup() {
        // No process-global state to reset for pure coordination tests
    }

    // --- AsyncCallbackFramework iron rules ---

    @Test
    void framework_oneHandlerFails_othersStillRun() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicInteger okCount = new AtomicInteger(0);

        framework.registerSync("message", event -> {
            throw new IllegalStateException("boom");
        });
        framework.registerSync("message", event -> {
            okCount.incrementAndGet();
        });

        CoordinationEvent event = EventMessage.builder()
                .eventType("message").payload(Map.of()).build();
        framework.trigger("message", event);

        assertThat(okCount.get()).isEqualTo(1);
    }

    @Test
    void framework_multipleHandlers_allRunInRegistrationOrder() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> order = new ArrayList<>();

        framework.registerSync("message", event -> order.add("first"));
        framework.registerSync("message", event -> order.add("second"));
        framework.registerSync("message", event -> order.add("third"));

        CoordinationEvent event = EventMessage.builder()
                .eventType("message").payload(Map.of()).build();
        framework.trigger("message", event);

        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    void framework_noCallbacksForEventKey_doesNotThrow() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        CoordinationEvent event = EventMessage.builder()
                .eventType("nonexistent").payload(Map.of()).build();
        // Should not throw
        framework.trigger("nonexistent", event);
    }

    // --- EventBus iron rules ---

    @Test
    void eventBus_startStop_setsRunningFlag() throws Exception {
        EventBus bus = new EventBus(TeamRole.LEADER);
        assertThat(bus.isRunning()).isFalse();

        bus.start(event -> {});
        assertThat(bus.isRunning()).isTrue();

        bus.stop();
        assertThat(bus.isRunning()).isFalse();
    }

    @Test
    void eventBus_stopIsIdempotent() throws Exception {
        EventBus bus = new EventBus(TeamRole.LEADER);
        bus.start(event -> {});
        bus.stop();
        bus.stop(); // second stop should not throw
        assertThat(bus.isRunning()).isFalse();
    }

    @Test
    void eventBus_wakeCallbackInvokedOnEvent() throws Exception {
        List<CoordinationEvent> woke = new ArrayList<>();
        EventBus bus = new EventBus(TeamRole.LEADER);
        bus.start(woke::add);

        CoordinationEvent event = EventMessage.builder()
                .eventType("message").payload(Map.of("msg", "hello")).build();
        bus.enqueue(event);

        Thread.sleep(100L);
        bus.stop();

        assertThat(woke).hasSize(1);
        assertThat(woke.get(0)).isInstanceOf(EventMessage.class);
        EventMessage msg = (EventMessage) woke.get(0);
        assertThat(msg.getEventType()).isEqualTo("message");
    }

    @Test
    void eventBus_multipleEvents_wakeInFifoOrder() throws Exception {
        List<String> order = new ArrayList<>();
        EventBus bus = new EventBus(TeamRole.LEADER);
        bus.start(event -> {
            if (event instanceof EventMessage msg) {
                order.add(msg.getEventType());
            }
        });

        for (String type : List.of("message", "task_completed", "broadcast")) {
            bus.enqueue(EventMessage.builder().eventType(type).payload(Map.of()).build());
        }

        Thread.sleep(150L);
        bus.stop();

        assertThat(order).containsExactly("message", "task_completed", "broadcast");
    }

    @Test
    void eventBus_noCallback_doesNotCrash() throws Exception {
        EventBus bus = new EventBus(TeamRole.LEADER);
        bus.start(); // no callback

        bus.enqueue(EventMessage.builder().eventType("message").payload(Map.of()).build());
        Thread.sleep(100L);
        bus.stop();
        // No exception = pass
    }

    @Test
    void eventBus_humanAgent_doesNotStartPollTimers() throws Exception {
        EventBus bus = new EventBus(TeamRole.HUMAN_AGENT);
        bus.start();

        assertThat(bus.isRunning()).isTrue();
        // Human agent bus should not have poll tasks running.
        // We verify indirectly: after a short wait, no POLL events are generated.
        List<String> pollEvents = new ArrayList<>();
        // Re-create with a capturing callback
        bus.stop();
        EventBus bus2 = new EventBus(TeamRole.HUMAN_AGENT, 50L, 50L);
        bus2.start(event -> {
            if (event instanceof InnerEventMessage inner) {
                pollEvents.add(inner.getEventType().name());
            }
        });

        Thread.sleep(200L);
        bus2.stop();

        assertThat(pollEvents).noneMatch(e -> e.equals("POLL_MAILBOX") || e.equals("POLL_TASK"));
    }

    @Test
    void eventBus_nonHuman_startsPollTimers() throws Exception {
        for (TeamRole role : List.of(TeamRole.LEADER, TeamRole.MEMBER)) {
            List<String> pollEvents = new ArrayList<>();
            EventBus bus = new EventBus(role, 50L, 50L);
            bus.start(event -> {
                if (event instanceof InnerEventMessage inner) {
                    pollEvents.add(inner.getEventType().name());
                }
            });

            Thread.sleep(200L);
            bus.stop();

            assertThat(pollEvents)
                    .as("Role %s should generate poll events", role)
                    .anyMatch(e -> e.equals("POLL_MAILBOX") || e.equals("POLL_TASK"));
        }
    }

    @Test
    void eventBus_humanAgent_resumePolls_staysNoop() throws Exception {
        EventBus bus = new EventBus(TeamRole.HUMAN_AGENT, 50L, 50L);
        List<String> pollEvents = new ArrayList<>();
        bus.start(event -> {
            if (event instanceof InnerEventMessage inner) {
                pollEvents.add(inner.getEventType().name());
            }
        });

        bus.pausePolls();
        assertThat(bus.isPollsPaused()).isTrue();

        bus.resumePolls();
        assertThat(bus.isPollsPaused()).isFalse();

        Thread.sleep(200L);
        bus.stop();

        // Still no poll events after resume
        assertThat(pollEvents).noneMatch(e -> e.equals("POLL_MAILBOX") || e.equals("POLL_TASK"));
    }

    @Test
    void eventBus_pauseAndResumePolls() throws Exception {
        List<String> pollEvents = new ArrayList<>();
        EventBus bus = new EventBus(TeamRole.LEADER, 50L, 50L);
        bus.start(event -> {
            if (event instanceof InnerEventMessage inner) {
                pollEvents.add(inner.getEventType().name());
            }
        });

        Thread.sleep(120L);
        int beforePause = pollEvents.size();
        assertThat(beforePause).isGreaterThan(0);

        bus.pausePolls();
        pollEvents.clear();
        Thread.sleep(120L);
        assertThat(pollEvents).isEmpty();

        bus.resumePolls();
        pollEvents.clear();
        Thread.sleep(120L);
        assertThat(pollEvents.size()).isGreaterThan(0);

        bus.stop();
    }

    // --- Handler narrow-protocol iron rule ---

    @Test
    void handlers_doNotHoldHostReference() {
        // Iron rule: BaseCoordinationHandler stores host as narrow protocols,
        // not the full DispatcherHost. Verify field types.
        boolean hasDirectHostField = false;
        for (java.lang.reflect.Field f : BaseCoordinationHandler.class.getDeclaredFields()) {
            if (f.getType().equals(DispatcherHost.class)) {
                hasDirectHostField = true;
            }
        }
        assertThat(hasDirectHostField)
                .as("BaseCoordinationHandler must not store a DispatcherHost field directly")
                .isFalse();
        // round and lifecycle are narrow protocol interfaces
        assertThat(BaseCoordinationHandler.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("round") && f.getType().equals(AgentRoundController.class))
                .anyMatch(f -> f.getName().equals("lifecycle") && f.getType().equals(TeamLifecycleController.class));
    }
}
