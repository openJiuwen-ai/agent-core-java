/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffRequest;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff request.
 *
 * <p>Mirrors Python's {@code test_handoff_request.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffRequest {

    private static final class FakeSession implements Session {
        private String sessionId;
        private int calls;

        FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            calls++;
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }

        void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        int getCalls() {
            return calls;
        }
    }

    @Nested
    class TestHandoffRequestConstruction {
        @Test
        void testInputMessageStored() {
            assertEquals("hello", new HandoffRequest("hello").getInputMessage());
        }

        @Test
        void testDictInputMessageStored() {
            Map<String, Object> message = Map.of("query", "q");
            assertSame(message, new HandoffRequest(message).getInputMessage());
        }

        @Test
        void testDefaultHistoryIsEmptyList() {
            assertEquals(List.of(), new HandoffRequest("hello").getHistory());
        }

        @Test
        void testDefaultSessionIsNone() {
            assertNull(new HandoffRequest("hello").getSession());
        }

        @Test
        void testCustomHistoryStored() {
            List<Map<String, Object>> history = new ArrayList<>(List.of(Map.of("agent", "a")));
            assertSame(history, new HandoffRequest("hello", history).getHistory());
        }

        @Test
        void testCustomSessionStored() {
            FakeSession session = new FakeSession("s1");
            assertSame(session, new HandoffRequest("hello", List.of(), session).getSession());
        }
    }

    @Nested
    class TestHandoffRequestSessionId {
        @Test
        void testSessionIdEmptyStringWhenNoSession() {
            assertEquals("", new HandoffRequest("hello").getSessionId());
        }

        @Test
        void testSessionIdFromSession() {
            assertEquals("sid-1", new HandoffRequest("hello", List.of(), new FakeSession("sid-1")).getSessionId());
        }

        @Test
        void testSessionIdCallsGetSessionIdOnce() {
            FakeSession session = new FakeSession("sid-1");
            HandoffRequest request = new HandoffRequest("hello", List.of(), session);
            request.getSessionId();
            assertEquals(1, session.getCalls());
        }

        @Test
        void testSessionIdIsStringType() {
            assertInstanceOf(String.class, new HandoffRequest("hello", List.of(), new FakeSession("sid-1"))
                    .getSessionId());
        }

        @Test
        void testSessionIdChangesWithSession() {
            FakeSession session = new FakeSession("sid-1");
            HandoffRequest request = new HandoffRequest("hello", List.of(), session);
            assertEquals("sid-1", request.getSessionId());
            session.setSessionId("sid-2");
            assertEquals("sid-2", request.getSessionId());
        }
    }

    @Nested
    class TestHandoffRequestHistoryIsolation {
        @Test
        void testDefaultHistoryNotSharedAcrossInstances() {
            HandoffRequest left = new HandoffRequest("a");
            HandoffRequest right = new HandoffRequest("b");
            left.getHistory().add(Map.of("agent", "a"));
            assertEquals(List.of(), right.getHistory());
        }

        @Test
        void testHistoryMutability() {
            HandoffRequest request = new HandoffRequest("a");
            request.getHistory().add(Map.of("agent", "a"));
            assertEquals(1, request.getHistory().size());
        }

        @Test
        void testHistoryLengthMatchesSupplied() {
            List<Map<String, Object>> history = List.of(Map.of("agent", "a"), Map.of("agent", "b"));
            assertEquals(2, new HandoffRequest("a", history).getHistory().size());
        }
    }
}
