/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.team.test_communicable_agent} in
 * {@code tests/unit_tests/multi_agent/team/test_communicable_agent.py}.</p>
 */
class CommunicableAgentMissingTest {

    @TestFactory
    Collection<DynamicTest> pythonCommunicableAgentCases() {
        return List.of(
                dynamic("TestCommunicableAgentBinding::test_is_bound_false_before_binding",
                        CommunicableAgentMissingTest::isBoundFalseBeforeBinding),
                dynamic("TestCommunicableAgentBinding::test_is_bound_true_after_bind_runtime",
                        CommunicableAgentMissingTest::isBoundTrueAfterBindRuntime),
                dynamic("TestCommunicableAgentBinding::test_runtime_property_returns_bound_runtime",
                        CommunicableAgentMissingTest::runtimePropertyReturnsBoundRuntime),
                dynamic("TestCommunicableAgentBinding::test_agent_id_property_returns_bound_id",
                        CommunicableAgentMissingTest::agentIdPropertyReturnsBoundId),
                dynamic("TestCommunicableAgentBinding::test_runtime_property_raises_when_not_bound",
                        CommunicableAgentMissingTest::runtimePropertyRaisesWhenNotBound),
                dynamic("TestCommunicableAgentBinding::test_agent_id_property_raises_when_not_bound",
                        CommunicableAgentMissingTest::agentIdPropertyRaisesWhenNotBound),
                dynamic("TestCommunicableAgentBinding::test_bind_runtime_idempotent_same_runtime_same_id",
                        CommunicableAgentMissingTest::bindRuntimeIdempotentSameRuntimeSameId),
                dynamic("TestCommunicableAgentBinding::test_bind_runtime_rebind_different_runtime_warns",
                        CommunicableAgentMissingTest::bindRuntimeRebindDifferentRuntimeWarns),
                dynamic("TestCommunicableAgentMessaging::test_send_method_accepts_session_id_parameter",
                        CommunicableAgentMissingTest::sendMethodAcceptsSessionIdParameter),
                dynamic("TestCommunicableAgentMessaging::test_send_method_accepts_timeout_parameter",
                        CommunicableAgentMissingTest::sendMethodAcceptsTimeoutParameter),
                dynamic("TestCommunicableAgentMessaging::test_publish_method_accepts_session_id_parameter",
                        CommunicableAgentMissingTest::publishMethodAcceptsSessionIdParameter),
                dynamic("TestCommunicableAgentMessaging::test_agent_has_send_method",
                        CommunicableAgentMissingTest::agentHasSendMethod),
                dynamic("TestCommunicableAgentMessaging::test_agent_has_publish_method",
                        CommunicableAgentMissingTest::agentHasPublishMethod),
                dynamic("TestCommunicableAgentMessaging::test_agent_has_subscribe_method",
                        CommunicableAgentMissingTest::agentHasSubscribeMethod),
                dynamic("TestCommunicableAgentMessaging::test_agent_has_unsubscribe_method",
                        CommunicableAgentMissingTest::agentHasUnsubscribeMethod)
        );
    }

    private static void isBoundFalseBeforeBinding() {
        SimpleAgent agent = new SimpleAgent();

        assertThat(agent.isBound()).isFalse();
    }

    private static void isBoundTrueAfterBindRuntime() {
        SimpleAgent agent = new SimpleAgent();
        TeamRuntime runtime = new TeamRuntime();

        agent.bindRuntime(runtime, "agent_x");

        assertThat(agent.isBound()).isTrue();
    }

    private static void runtimePropertyReturnsBoundRuntime() {
        SimpleAgent agent = new SimpleAgent();
        TeamRuntime runtime = new TeamRuntime();

        agent.bindRuntime(runtime, "agent_x");

        assertSame(runtime, agent.getRuntime());
    }

    private static void agentIdPropertyReturnsBoundId() {
        SimpleAgent agent = new SimpleAgent();
        TeamRuntime runtime = new TeamRuntime();

        agent.bindRuntime(runtime, "my_agent");

        assertThat(agent.getAgentId()).isEqualTo("my_agent");
    }

    private static void runtimePropertyRaisesWhenNotBound() {
        SimpleAgent agent = new SimpleAgent();

        assertThrows(RuntimeException.class, agent::getRuntime);
    }

    private static void agentIdPropertyRaisesWhenNotBound() {
        SimpleAgent agent = new SimpleAgent();

        assertThrows(RuntimeException.class, agent::getAgentId);
    }

    private static void bindRuntimeIdempotentSameRuntimeSameId() {
        SimpleAgent agent = new SimpleAgent();
        TeamRuntime runtime = new TeamRuntime();

        agent.bindRuntime(runtime, "agent_x");
        agent.bindRuntime(runtime, "agent_x");

        assertThat(agent.getAgentId()).isEqualTo("agent_x");
    }

    private static void bindRuntimeRebindDifferentRuntimeWarns() {
        SimpleAgent agent = new SimpleAgent();
        TeamRuntime runtime1 = new TeamRuntime();
        TeamRuntime runtime2 = new TeamRuntime();

        agent.bindRuntime(runtime1, "agent_x");
        agent.bindRuntime(runtime2, "agent_y");

        assertThat(agent.getAgentId()).isEqualTo("agent_y");
        assertSame(runtime2, agent.getRuntime());
    }

    private static void sendMethodAcceptsSessionIdParameter() throws NoSuchMethodException {
        Method method = CommunicableAgent.class.getMethod("send", Object.class, String.class, String.class);

        assertThat(method.getParameters()[2].getName()).isEqualTo("sessionId");
        assertThat(method.getParameters()[2].getType()).isEqualTo(String.class);
    }

    private static void sendMethodAcceptsTimeoutParameter() throws NoSuchMethodException {
        Method method = CommunicableAgent.class.getMethod(
                "send", Object.class, String.class, String.class, Double.class);

        assertThat(method.getParameters()[2].getName()).isEqualTo("sessionId");
        assertThat(method.getParameters()[3].getName()).isEqualTo("timeout");
        assertThat(method.getParameters()[3].getType()).isEqualTo(Double.class);
    }

    private static void publishMethodAcceptsSessionIdParameter() throws NoSuchMethodException {
        Method method = CommunicableAgent.class.getMethod("publish", Object.class, String.class, String.class);

        assertThat(method.getParameters()[2].getName()).isEqualTo("sessionId");
        assertThat(method.getParameters()[2].getType()).isEqualTo(String.class);
    }

    private static void agentHasSendMethod() {
        assertThat(hasMethodNamed("send")).isTrue();
    }

    private static void agentHasPublishMethod() {
        assertThat(hasMethodNamed("publish")).isTrue();
    }

    private static void agentHasSubscribeMethod() {
        assertThat(hasMethodNamed("subscribe")).isTrue();
    }

    private static void agentHasUnsubscribeMethod() {
        assertThat(hasMethodNamed("unsubscribe")).isTrue();
    }

    private static boolean hasMethodNamed(String name) {
        return List.of(CommunicableAgent.class.getMethods()).stream()
                .anyMatch(method -> method.getName().equals(name));
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static final class SimpleAgent implements CommunicableAgent {
    }
}
