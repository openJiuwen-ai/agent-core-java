/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRuntimeOverlayTest {

    @Test
    void registerAgentAutoBindsCommunicableAgent() {
        TeamRuntime runtime = new TeamRuntime();
        EchoAgent agent = new EchoAgent();

        runtime.registerAgent(new AgentCard("echo", "Echo", "echo agent"), () -> agent);
        Object response = runtime.send("hello", "echo", "sender", "session-1").join();

        assertEquals("echo:hello:session-1", response);
        assertTrue(agent.isBound());
        assertSame(runtime, agent.getRuntime());
        assertEquals("echo", agent.getAgentId());
    }

    @Test
    void publishFansOutToMatchingSubscribersOnly() {
        TeamRuntime runtime = new TeamRuntime();
        RecordingAgent codeAgent = new RecordingAgent();
        RecordingAgent otherAgent = new RecordingAgent();

        runtime.registerAgent(new AgentCard("code", "Code", "code subscriber"), () -> codeAgent);
        runtime.registerAgent(new AgentCard("other", "Other", "other subscriber"), () -> otherAgent);
        runtime.subscribe("code", "code_*").join();
        runtime.subscribe("other", "other_*").join();

        runtime.publish("done", "code_events", "sender", "session-2").join();

        assertEquals(List.of("done"), codeAgent.messages);
        assertTrue(otherAgent.messages.isEmpty());
        assertEquals(2, runtime.getSubscriptionCount());
    }

    @Test
    void cleanupSessionRemovesSessionScopedTopics() {
        TeamRuntime runtime = new TeamRuntime();
        runtime.registerAgent(new AgentCard("echo", "Echo", "echo agent"), EchoAgent::new);

        runtime.send("hello", "echo", "sender", "s-clean").join();
        runtime.publish("event", "topic", "sender", "s-clean").join();

        assertTrue(runtime.getMessageBus().getActiveSubscriptions().contains("default_s-clean__p2p__"));
        assertTrue(runtime.getMessageBus().getActiveSubscriptions().contains("default_s-clean__pubsub__"));

        runtime.cleanupSession("s-clean").join();

        assertFalse(runtime.getMessageBus().getActiveSubscriptions().contains("default_s-clean__p2p__"));
        assertFalse(runtime.getMessageBus().getActiveSubscriptions().contains("default_s-clean__pubsub__"));
    }

    @Test
    void missingRecipientAndSenderRaiseRuntimeErrors() {
        TeamRuntime runtime = new TeamRuntime();
        runtime.registerAgent(new AgentCard("echo", "Echo", "echo agent"), EchoAgent::new);

        assertThrows(RuntimeException.class, () -> runtime.send("hello", "", "sender").join());
        assertThrows(RuntimeException.class, () -> runtime.send("hello", "echo", "").join());
        assertThrows(RuntimeException.class, () -> runtime.publish("hello", "", "sender").join());
    }

    @Test
    void functionProvidersAreInvokedWithMessagePayload() {
        TeamRuntime runtime = new TeamRuntime();
        AtomicReference<Object> seen = new AtomicReference<>();

        runtime.registerAgent(
                new AgentCard("fn", "Fn", "function agent"),
                () -> (java.util.function.Function<Object, Object>) message -> {
                    seen.set(message);
                    return CompletableFuture.completedFuture("ok:" + message);
                }
        );

        assertEquals("ok:payload", runtime.send("payload", "fn", "sender").join());
        assertEquals("payload", seen.get());
    }

    static final class EchoAgent implements CommunicableAgent {
        Object invoke(Object message, Object session) {
            return "echo:" + message + ":" + session;
        }
    }

    static final class RecordingAgent implements CommunicableAgent {
        private final List<Object> messages = new ArrayList<>();

        Object invoke(Object message, Object session) {
            messages.add(message);
            return null;
        }
    }
}
