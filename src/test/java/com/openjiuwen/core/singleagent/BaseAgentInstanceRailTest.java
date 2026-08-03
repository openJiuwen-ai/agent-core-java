/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BaseAgentInstanceRailTest {
    @Test
    void registerAndUnregisterManageLifecycleAndCallbacks() {
        TestAgent agent = new TestAgent("agent-instance-rail");
        LifecycleRail rail = new LifecycleRail("instance");

        BaseAgent registered = agent.registerInstanceRail(rail).toCompletableFuture().join();
        agent.executeCallbacks(AgentCallbackEvent.BEFORE_INVOKE, "input", null, null)
                .toCompletableFuture().join();

        assertSame(agent, registered);
        assertSame(agent, rail.initializedAgent);
        assertEquals(List.of("instance"), rail.calls);

        BaseAgent unregistered = agent.unregisterInstanceRail(rail).toCompletableFuture().join();
        agent.executeCallbacks(AgentCallbackEvent.BEFORE_INVOKE, "input", null, null)
                .toCompletableFuture().join();

        assertSame(agent, unregistered);
        assertSame(agent, rail.uninitializedAgent);
        assertEquals(1, rail.initCalls);
        assertEquals(1, rail.uninitCalls);
        assertEquals(List.of("instance"), rail.calls);
        assertFalse(agent.getAgentCallbackManager().hasInstanceHooks(AgentCallbackEvent.BEFORE_INVOKE));
    }

    @Test
    void sameCardIdAgentsKeepInstanceRailsSeparate() {
        TestAgent first = new TestAgent("same-id");
        TestAgent second = new TestAgent("same-id");
        LifecycleRail firstRail = new LifecycleRail("first");
        LifecycleRail secondRail = new LifecycleRail("second");

        first.registerInstanceRail(firstRail).toCompletableFuture().join();
        second.registerInstanceRail(secondRail).toCompletableFuture().join();

        first.executeCallbacks(AgentCallbackEvent.BEFORE_INVOKE, "input", null, null)
                .toCompletableFuture().join();

        assertEquals(List.of("first"), firstRail.calls);
        assertEquals(List.of(), secondRail.calls);
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent(String id) {
            super(new AgentCard(id, id, "instance rail test agent"));
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }
    }

    private static final class LifecycleRail extends AgentRail {
        private final String marker;
        private final List<String> calls = new ArrayList<>();
        private BaseAgent initializedAgent;
        private BaseAgent uninitializedAgent;
        private int initCalls;
        private int uninitCalls;

        private LifecycleRail(String marker) {
            this.marker = marker;
        }

        @Override
        public void init(BaseAgent agent) {
            initializedAgent = agent;
            initCalls++;
        }

        @Override
        public void uninit(BaseAgent agent) {
            uninitializedAgent = agent;
            uninitCalls++;
        }

        @Override
        public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
            calls.add(marker);
            return CompletableFuture.completedFuture(null);
        }
    }
}
