/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffRequest;
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

    @Nested
    class TestHandoffRequestConstruction {

        @Test
        void testInputMessageStored() {
            HandoffRequest req = new HandoffRequest("hello");
            assertEquals("hello", req.getInputMessage());
        }

        @Test
        void testMapInputMessageStored() {
            Map<String, Object> message = Map.of("query", "what is 2+2");
            HandoffRequest req = new HandoffRequest(message);
            assertSame(message, req.getInputMessage());
        }

        @Test
        void testDefaultHistoryIsEmptyList() {
            HandoffRequest req = new HandoffRequest("x");
            assertEquals(List.of(), req.getHistory());
        }

        @Test
        void testCustomHistoryStored() {
            List<Map<String, Object>> history = List.of(Map.of("agent", "a", "output", Map.of("ok", true)));
            HandoffRequest req = new HandoffRequest("x", history);
            assertEquals(history, req.getHistory());
        }
    }

    @Nested
    class TestHandoffRequestSessionId {

        @Test
        void testSessionIdEmptyStringWhenNoSession() {
            HandoffRequest req = new HandoffRequest("hello");
            assertEquals("", req.getSessionId());
        }

        @Test
        void testSessionIdStoredWhenProvided() {
            HandoffRequest req = new HandoffRequest("hi", new ArrayList<>(), "sid-123");
            assertEquals("sid-123", req.getSessionId());
        }

        @Test
        void testSessionIdIsStringType() {
            HandoffRequest req = new HandoffRequest("x");
            assertInstanceOf(String.class, req.getSessionId());
        }
    }

    @Nested
    class TestHandoffRequestHistoryIsolation {

        @Test
        void testDefaultHistoryNotSharedAcrossInstances() {
            HandoffRequest req1 = new HandoffRequest("a");
            HandoffRequest req2 = new HandoffRequest("b");
            req1.getHistory().add(Map.of("agent", "x", "output", Map.of()));
            assertEquals(List.of(), req2.getHistory());
        }

        @Test
        void testHistoryMutability() {
            HandoffRequest req = new HandoffRequest("x");
            req.getHistory().add(Map.of("agent", "a", "output", Map.of()));
            assertEquals(1, req.getHistory().size());
        }

        @Test
        void testHistoryLengthMatchesSupplied() {
            List<Map<String, Object>> history = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                history.add(Map.of("agent", "agent_" + i, "output", Map.of()));
            }
            HandoffRequest req = new HandoffRequest("x", history);
            assertEquals(5, req.getHistory().size());
        }
    }
}
