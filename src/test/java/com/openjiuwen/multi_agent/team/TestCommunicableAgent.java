/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommunicableAgent mixin behavior.
 *
 * <p>Mirrors Python's {@code test_communicable_agent.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestCommunicableAgent {

    static class SimpleAgent implements CommunicableAgent {
    }

    @Nested
    class TestCommunicableAgentBinding {

        @Test
        void testIsBoundFalseBeforeBinding() {
            assertFalse(new SimpleAgent().isBound());
        }

        @Test
        void testIsBoundTrueAfterBindRuntime() {
            SimpleAgent agent = new SimpleAgent();
            agent.bindRuntime(new TeamRuntime(), "agent_x");

            assertTrue(agent.isBound());
        }

        @Test
        void testRuntimePropertyReturnsBoundRuntime() {
            SimpleAgent agent = new SimpleAgent();
            TeamRuntime runtime = new TeamRuntime();

            agent.bindRuntime(runtime, "agent_x");

            assertSame(runtime, agent.getRuntime());
        }

        @Test
        void testAgentIdPropertyReturnsBoundId() {
            SimpleAgent agent = new SimpleAgent();
            agent.bindRuntime(new TeamRuntime(), "my_agent");

            assertEquals("my_agent", agent.getAgentId());
        }

        @Test
        void testRuntimePropertyRaisesWhenNotBound() {
            assertThrows(Exception.class, () -> new SimpleAgent().getRuntime());
        }

        @Test
        void testAgentIdPropertyRaisesWhenNotBound() {
            assertThrows(Exception.class, () -> new SimpleAgent().getAgentId());
        }

        @Test
        void testBindRuntimeIdempotentSameRuntimeSameId() {
            SimpleAgent agent = new SimpleAgent();
            TeamRuntime runtime = new TeamRuntime();

            agent.bindRuntime(runtime, "agent_x");
            agent.bindRuntime(runtime, "agent_x");

            assertEquals("agent_x", agent.getAgentId());
            assertSame(runtime, agent.getRuntime());
        }

        @Test
        void testBindRuntimeRebindDifferentRuntimeWarns() {
            SimpleAgent agent = new SimpleAgent();
            TeamRuntime runtime1 = new TeamRuntime();
            TeamRuntime runtime2 = new TeamRuntime();

            agent.bindRuntime(runtime1, "agent_x");
            agent.bindRuntime(runtime2, "agent_y");

            assertEquals("agent_y", agent.getAgentId());
            assertSame(runtime2, agent.getRuntime());
        }
    }

    @Nested
    class TestCommunicableAgentMessaging {
        private SimpleAgent agent;
        private TeamRuntime runtime;

        @BeforeEach
        void setup() {
            agent = new SimpleAgent();
            runtime = new TeamRuntime();
            agent.bindRuntime(runtime, "sender_agent");
        }

        @Test
        void testSendMethodAcceptsSessionIdParameter() throws NoSuchMethodException {
            Method method = CommunicableAgent.class.getMethod("send", Object.class, String.class, String.class);

            assertEquals(3, method.getParameterCount());
        }

        @Test
        void testSendMethodAcceptsTimeoutParameter() throws NoSuchMethodException {
            Method method = CommunicableAgent.class.getMethod(
                    "send", Object.class, String.class, String.class, Double.class);

            assertEquals(4, method.getParameterCount());
        }

        @Test
        void testPublishMethodAcceptsSessionIdParameter() throws NoSuchMethodException {
            Method method = CommunicableAgent.class.getMethod("publish", Object.class, String.class, String.class);

            assertEquals(3, method.getParameterCount());
        }

        @Test
        void testAgentHasSendMethod() {
            assertDoesNotThrow(() -> agent.getClass().getMethod("send", Object.class, String.class, String.class));
        }

        @Test
        void testAgentHasPublishMethod() {
            assertDoesNotThrow(() -> agent.getClass().getMethod("publish", Object.class, String.class, String.class));
        }

        @Test
        void testAgentHasSubscribeMethod() {
            assertDoesNotThrow(() -> agent.getClass().getMethod("subscribe", String.class));
        }

        @Test
        void testAgentHasUnsubscribeMethod() {
            assertDoesNotThrow(() -> agent.getClass().getMethod("unsubscribe", String.class));
        }
    }
}
