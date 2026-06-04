/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.Interrupt;
import com.openjiuwen.core.multiagent.teams.handoff.TeamInterruptSignal;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for interrupt handling.
 *
 * <p>Mirrors Python's {@code test_interrupt.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestInterrupt {

    private static final class RecordingSession implements Session {
        private int postRunCalls;
        private RuntimeException failure;

        @Override
        public String getSessionId() {
            return "sid";
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }

        public void postRun() {
            postRunCalls++;
            if (failure != null) {
                throw failure;
            }
        }

        int getPostRunCalls() {
            return postRunCalls;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }
    }

    @Nested
    class TestTeamInterruptSignal {
        @Test
        void testResultStored() {
            Map<String, Object> payload = Map.of("result_type", "interrupt");
            TeamInterruptSignal signal = new TeamInterruptSignal(payload);
            assertSame(payload, signal.getResult());
        }

        @Test
        void testMessageDefaultsToNone() {
            assertTrue(new TeamInterruptSignal(Map.of()).getMessage().isEmpty());
        }

        @Test
        void testCustomMessage() {
            assertEquals("paused", new TeamInterruptSignal(Map.of(), "paused").getMessage().orElseThrow());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testResultTypePreserved() {
            Map<String, Object> payload = Map.of("result_type", "interrupt", "data", 42);
            TeamInterruptSignal signal = new TeamInterruptSignal(payload);
            Map<String, Object> result = (Map<String, Object>) signal.getResult();
            assertEquals("interrupt", result.get("result_type"));
            assertEquals(42, result.get("data"));
        }
    }

    @Nested
    class TestExtractInterruptSignal {
        @Test
        void testInterruptResultReturnsSignal() {
            Map<String, Object> result = Map.of("result_type", "interrupt", "message", "need input");
            TeamInterruptSignal signal = Interrupt.extractInterruptSignal(result).orElseThrow();
            assertSame(result, signal.getResult());
        }

        @Test
        void testNonInterruptResultReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(Map.of("result_type", "answer")).isEmpty());
        }

        @Test
        void testNonDictResultReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal("interrupt").isEmpty());
        }

        @Test
        void testNoneResultReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(null).isEmpty());
        }

        @Test
        void testBothNoneReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(null, null).isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testAgentInterruptExcReturnsSignal() {
            TeamInterruptSignal signal = Interrupt.extractInterruptSignal(null, new AgentInterrupt("waiting for user"))
                    .orElseThrow();
            assertEquals("waiting for user", signal.getMessage().orElseThrow());
            assertEquals("interrupt", ((Map<String, Object>) signal.getResult()).get("result_type"));
        }

        @Test
        void testNonAgentInterruptExcReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(null, new IllegalArgumentException("err")).isEmpty());
        }

        @Test
        void testInterruptResultTakesPriorityOverExc() {
            Map<String, Object> result = Map.of("result_type", "interrupt");
            TeamInterruptSignal signal = Interrupt.extractInterruptSignal(result, new AgentInterrupt("from exc"))
                    .orElseThrow();
            assertSame(result, signal.getResult());
        }

        @Test
        void testNonInterruptResultFallsThroughToExc() {
            TeamInterruptSignal signal = Interrupt.extractInterruptSignal(
                    Map.of("result_type", "answer"), new AgentInterrupt("from exc")).orElseThrow();
            assertEquals("from exc", signal.getMessage().orElseThrow());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testAgentInterruptResultMessageInPayload() {
            TeamInterruptSignal signal = Interrupt.extractInterruptSignal(null, new AgentInterrupt("pause reason"))
                    .orElseThrow();
            assertEquals("pause reason", ((Map<String, Object>) signal.getResult()).get("message"));
        }

        @Test
        void testListResultReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(List.of(Map.of("result_type", "interrupt"))).isEmpty());
        }

        @Test
        void testMissingResultTypeReturnsNone() {
            assertTrue(Interrupt.extractInterruptSignal(Map.of("data", "x")).isEmpty());
        }
    }

    @Nested
    class TestFlushTeamSession {
        @Test
        void testNoneSessionReturnsSilently() {
            assertDoesNotThrow(() -> Interrupt.flushTeamSession(null));
        }

        @Test
        void testNoneSessionDoesNotCallPostRun() {
            RecordingSession session = new RecordingSession();
            Interrupt.flushTeamSession(null);
            assertEquals(0, session.getPostRunCalls());
        }

        @Test
        void testPostRunCalledOnce() {
            RecordingSession session = new RecordingSession();
            Interrupt.flushTeamSession(session);
            assertEquals(1, session.getPostRunCalls());
        }

        @Test
        void testExceptionDoesNotPropagate() {
            RecordingSession session = new RecordingSession();
            session.failWith(new RuntimeException("checkpointer down"));
            assertDoesNotThrow(() -> Interrupt.flushTeamSession(session));
        }

        @Test
        void testPostRunAttemptedWhenFailureOccurs() {
            RecordingSession session = new RecordingSession();
            session.failWith(new RuntimeException("storage unavailable"));
            Interrupt.flushTeamSession(session);
            assertEquals(1, session.getPostRunCalls());
        }

        @Test
        void testSecondFlushCallsPostRunAgain() {
            RecordingSession session = new RecordingSession();
            Interrupt.flushTeamSession(session);
            Interrupt.flushTeamSession(session);
            assertEquals(2, session.getPostRunCalls());
        }

        @Test
        void testFailureDoesNotClearSessionIdentity() {
            RecordingSession session = new RecordingSession();
            session.failWith(new RuntimeException("fail"));
            Interrupt.flushTeamSession(session);
            assertEquals("sid", session.getSessionId());
        }

        @Test
        void testNoExceptionOnSuccess() {
            RecordingSession session = new RecordingSession();
            assertDoesNotThrow(() -> Interrupt.flushTeamSession(session));
        }
    }
}
