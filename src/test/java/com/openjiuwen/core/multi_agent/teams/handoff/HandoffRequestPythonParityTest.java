/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.session.AgentTeamSession;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for handoff requests.
 *
 * <p>Mirrors Python's
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_request.py}.</p>
 */
class HandoffRequestPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_request.py";

    @TestFactory
    Collection<DynamicTest> pythonHandoffRequestCases() {
        return List.of(
                caseOf("TestHandoffRequestConstruction::test_input_message_stored",
                        HandoffRequestPythonParityTest::inputMessageStored),
                caseOf("TestHandoffRequestConstruction::test_dict_input_message_stored",
                        HandoffRequestPythonParityTest::dictInputMessageStored),
                caseOf("TestHandoffRequestConstruction::test_default_history_is_empty_list",
                        HandoffRequestPythonParityTest::defaultHistoryIsEmptyList),
                caseOf("TestHandoffRequestConstruction::test_default_session_is_none",
                        HandoffRequestPythonParityTest::defaultSessionIsNone),
                caseOf("TestHandoffRequestConstruction::test_custom_history_stored",
                        HandoffRequestPythonParityTest::customHistoryStored),
                caseOf("TestHandoffRequestConstruction::test_custom_session_stored",
                        HandoffRequestPythonParityTest::customSessionStored),
                caseOf("TestHandoffRequestSessionId::test_session_id_empty_string_when_no_session",
                        HandoffRequestPythonParityTest::sessionIdEmptyStringWhenNoSession),
                caseOf("TestHandoffRequestSessionId::test_session_id_from_session",
                        HandoffRequestPythonParityTest::sessionIdFromSession),
                caseOf("TestHandoffRequestSessionId::test_session_id_calls_get_session_id_once",
                        HandoffRequestPythonParityTest::sessionIdCallsGetSessionIdOnce),
                caseOf("TestHandoffRequestSessionId::test_session_id_is_string_type",
                        HandoffRequestPythonParityTest::sessionIdIsStringType),
                caseOf("TestHandoffRequestSessionId::test_session_id_changes_with_session",
                        HandoffRequestPythonParityTest::sessionIdChangesWithSession),
                caseOf("TestHandoffRequestHistoryIsolation::test_default_history_not_shared_across_instances",
                        HandoffRequestPythonParityTest::defaultHistoryNotSharedAcrossInstances),
                caseOf("TestHandoffRequestHistoryIsolation::test_history_mutability",
                        HandoffRequestPythonParityTest::historyMutability),
                caseOf("TestHandoffRequestHistoryIsolation::test_history_length_matches_supplied",
                        HandoffRequestPythonParityTest::historyLengthMatchesSupplied)
        );
    }

    private static DynamicTest caseOf(String pythonNode, ExecutableCase executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable::run);
    }

    private static void inputMessageStored() {
        HandoffRequest request = new HandoffRequest("hello");

        assertThat(request.getInputMessage()).isEqualTo("hello");
    }

    private static void dictInputMessageStored() {
        Map<String, Object> message = Map.of("query", "what is 2+2");
        HandoffRequest request = new HandoffRequest(message);

        assertThat(request.getInputMessage()).isSameAs(message);
    }

    private static void defaultHistoryIsEmptyList() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getHistory()).isEmpty();
    }

    private static void defaultSessionIsNone() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getSession()).isNull();
    }

    private static void customHistoryStored() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of("agent", "a", "output", Map.of("ok", true)));
        HandoffRequest request = new HandoffRequest("x", history, null);

        assertThat(request.getHistory()).isEqualTo(history);
    }

    private static void customSessionStored() {
        AgentTeamSession session = new AgentTeamSession("sid-custom", Map.of(), "team");
        HandoffRequest request = new HandoffRequest("x", null, session);

        assertThat(request.getSession()).isSameAs(session);
    }

    private static void sessionIdEmptyStringWhenNoSession() {
        HandoffRequest request = new HandoffRequest("hello");

        assertThat(request.getSessionId()).isEqualTo("");
    }

    private static void sessionIdFromSession() {
        CountingSession session = new CountingSession("sid-123");
        HandoffRequest request = new HandoffRequest("hi", null, session);

        assertThat(request.getSessionId()).isEqualTo("sid-123");
    }

    private static void sessionIdCallsGetSessionIdOnce() {
        CountingSession session = new CountingSession("abc");
        HandoffRequest request = new HandoffRequest("hi", null, session);

        String ignored = request.getSessionId();

        assertThat(ignored).isEqualTo("abc");
        assertThat(session.callCount).isEqualTo(1);
    }

    private static void sessionIdIsStringType() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getSessionId()).isInstanceOf(String.class);
    }

    private static void sessionIdChangesWithSession() {
        CountingSession session = new CountingSession("new-id");
        HandoffRequest request = new HandoffRequest("x", null, session);

        assertThat(request.getSessionId()).isEqualTo("new-id");
    }

    private static void defaultHistoryNotSharedAcrossInstances() {
        HandoffRequest first = new HandoffRequest("a");
        HandoffRequest second = new HandoffRequest("b");

        first.getHistory().add(Map.of("agent", "x", "output", Map.of()));

        assertThat(second.getHistory()).isEmpty();
    }

    private static void historyMutability() {
        HandoffRequest request = new HandoffRequest("x");

        request.getHistory().add(Map.of("agent", "a", "output", Map.of()));

        assertThat(request.getHistory()).hasSize(1);
    }

    private static void historyLengthMatchesSupplied() {
        List<Map<String, Object>> history = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            history.add(Map.of("agent", "agent_" + index, "output", Map.of()));
        }
        HandoffRequest request = new HandoffRequest("x", history, null);

        assertThat(request.getHistory()).hasSize(5);
    }

    @FunctionalInterface
    private interface ExecutableCase {
        void run() throws Exception;
    }

    private static final class CountingSession extends AgentTeamSession {
        private int callCount;

        private CountingSession(String sessionId) {
            super(sessionId, Map.of(), "team");
        }

        @Override
        public String getSessionId() {
            callCount++;
            return super.getSessionId();
        }
    }
}
