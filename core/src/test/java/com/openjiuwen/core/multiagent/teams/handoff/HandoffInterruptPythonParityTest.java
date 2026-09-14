/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_interrupt} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_interrupt.py}.
 */
@DisplayName("Python parity for handoff interrupt helpers")
class HandoffInterruptPythonParityTest {

    @Nested
    @DisplayName("TeamInterruptSignal")
    class TeamInterruptSignalTests {

        @Test
        void testResultStored() {
            Map<String, Object> payload = Map.of("result_type", "interrupt");
            TeamInterruptSignal signal = new TeamInterruptSignal(payload);

            assertThat(signal.getResult()).isSameAs(payload);
        }

        @Test
        void testMessageDefaultsToNone() {
            assertThat(new TeamInterruptSignal(Map.of()).getMessage()).isEmpty();
        }

        @Test
        void testCustomMessage() {
            TeamInterruptSignal signal = new TeamInterruptSignal(Map.of(), "paused");

            assertThat(signal.getMessage()).contains("paused");
        }

        @Test
        void testResultTypePreserved() {
            Map<String, Object> payload = Map.of("result_type", "interrupt", "data", 42);
            TeamInterruptSignal signal = new TeamInterruptSignal(payload);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) signal.getResult();

            assertThat(result).containsEntry("result_type", "interrupt").containsEntry("data", 42);
        }
    }

    @Nested
    @DisplayName("extractInterruptSignal")
    class ExtractInterruptSignalTests {

        @Test
        void testInterruptResultReturnsSignal() {
            Map<String, Object> result = Map.of("result_type", "interrupt", "message", "need input");

            Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(result);

            assertThat(signal).isPresent();
            assertThat(signal.orElseThrow().getResult()).isSameAs(result);
        }

        @Test
        void testNonInterruptResultReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(Map.of("result_type", "answer"))).isEmpty();
        }

        @Test
        void testNonDictResultReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal("interrupt")).isEmpty();
        }

        @Test
        void testNoneResultReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(null)).isEmpty();
        }

        @Test
        void testBothNoneReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(null, null)).isEmpty();
        }

        @Test
        void testAgentInterruptExcReturnsSignal() {
            AgentInterrupt exception = new AgentInterrupt("waiting for user");

            Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(null, exception);

            assertThat(signal).isPresent();
            assertThat(signal.orElseThrow().getMessage()).contains("waiting for user");
            assertThat(resultMap(signal)).containsEntry("result_type", "interrupt");
        }

        @Test
        void testNonAgentInterruptExcReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(null, new IllegalArgumentException("err"))).isEmpty();
        }

        @Test
        void testInterruptResultTakesPriorityOverExc() {
            Map<String, Object> result = Map.of("result_type", "interrupt");
            AgentInterrupt exception = new AgentInterrupt("from exc");

            Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(result, exception);

            assertThat(signal).isPresent();
            assertThat(signal.orElseThrow().getResult()).isSameAs(result);
        }

        @Test
        void testNonInterruptResultFallsThroughToExc() {
            Map<String, Object> result = Map.of("result_type", "answer");
            AgentInterrupt exception = new AgentInterrupt("from exc");

            Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(result, exception);

            assertThat(signal).isPresent();
            assertThat(signal.orElseThrow().getMessage()).contains("from exc");
        }

        @Test
        void testAgentInterruptResultMessageInPayload() {
            Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(
                    null,
                    new AgentInterrupt("pause reason")
            );

            assertThat(resultMap(signal)).containsEntry("message", "pause reason");
        }

        @Test
        void testListResultReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(List.of(Map.of("result_type", "interrupt")))).isEmpty();
        }

        @Test
        void testMissingResultTypeReturnsNone() {
            assertThat(HandoffInterrupts.extractInterruptSignal(Map.of("data", "x"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("flushTeamSession")
    class FlushTeamSessionTests {

        @Test
        void testNoneSessionReturnsSilently() {
            HandoffInterrupts.flushTeamSession(null);
        }

        @Test
        void testNoneSessionDoesNotCommit() {
            RecordingTeamSession session = new RecordingTeamSession();

            HandoffInterrupts.flushTeamSession(null);

            assertThat(session.closeCalls).isZero();
            assertThat(session.commitCalls).isZero();
        }

        @Test
        void testCloseStreamAndCommitCalledOnce() {
            RecordingTeamSession session = new RecordingTeamSession();

            HandoffInterrupts.flushTeamSession(session);

            assertThat(session.closeCalls).isEqualTo(1);
            assertThat(session.commitCalls).isEqualTo(1);
        }

        @Test
        void testExceptionDoesNotPropagate() {
            RecordingTeamSession session = new RecordingTeamSession();
            session.commitFailure = new RuntimeException("checkpointer down");
            registerLogger();
            try {
                HandoffInterrupts.flushTeamSession(session);

                assertThat(session.closeCalls).isEqualTo(1);
                assertThat(session.commitCalls).isEqualTo(1);
            } finally {
                LogManager.reset();
            }
        }

        @Test
        void testWarningLoggedOnFailure() {
            RecordingTeamSession session = failingSession(new RuntimeException("storage unavailable"));
            RecordingLogger logger = registerLogger();
            try {
                HandoffInterrupts.flushTeamSession(session);

                assertThat(logger.warnings).hasSize(1);
            } finally {
                LogManager.reset();
            }
        }

        @Test
        void testWarningLoggedWithExcInfo() {
            RuntimeException failure = new RuntimeException("redis timeout");
            RecordingTeamSession session = failingSession(failure);
            RecordingLogger logger = registerLogger();
            try {
                HandoffInterrupts.flushTeamSession(session);

                assertThat(logger.warnings).hasSize(1);
                assertThat(logger.warnings.get(0).args).contains(failure);
            } finally {
                LogManager.reset();
            }
        }

        @Test
        void testWarningMessageContainsFlushOrCheckpointer() {
            RecordingTeamSession session = failingSession(new RuntimeException("fail"));
            RecordingLogger logger = registerLogger();
            try {
                HandoffInterrupts.flushTeamSession(session);

                String combined = logger.warnings.get(0).message.toLowerCase();
                assertThat(combined).containsAnyOf("flush", "checkpointer");
            } finally {
                LogManager.reset();
            }
        }

        @Test
        void testNoWarningOnSuccess() {
            RecordingTeamSession session = new RecordingTeamSession();
            RecordingLogger logger = registerLogger();
            try {
                HandoffInterrupts.flushTeamSession(session);

                assertThat(logger.warnings).isEmpty();
            } finally {
                LogManager.reset();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(Optional<TeamInterruptSignal> signal) {
        assertThat(signal).isPresent();
        return (Map<String, Object>) signal.orElseThrow().getResult();
    }

    private static RecordingTeamSession failingSession(RuntimeException failure) {
        RecordingTeamSession session = new RecordingTeamSession();
        session.commitFailure = failure;
        return session;
    }

    private static RecordingLogger registerLogger() {
        RecordingLogger logger = new RecordingLogger();
        LogManager.registerLogger("multi_agent", logger);
        return logger;
    }

    private static final class RecordingTeamSession extends AgentTeamSession {

        private int closeCalls;
        private int commitCalls;
        private RuntimeException commitFailure;

        private RecordingTeamSession() {
            super("test-session", Map.of(), "test-team");
        }

        @Override
        public void closeStream() {
            closeCalls++;
        }

        @Override
        public void commit() {
            commitCalls++;
            if (commitFailure != null) {
                throw commitFailure;
            }
        }
    }

    private record WarningRecord(String message, Object[] args) {
    }

    private static final class RecordingLogger implements LoggerProtocol {

        private final List<WarningRecord> warnings = new ArrayList<>();

        @Override
        public void debug(String msg, Object... args) {
        }

        @Override
        public void info(String msg, Object... args) {
        }

        @Override
        public void warning(String msg, Object... args) {
            warnings.add(new WarningRecord(msg, args));
        }

        @Override
        public void error(String msg, Object... args) {
        }

        @Override
        public void critical(String msg, Object... args) {
        }

        @Override
        public void exception(String msg, Throwable t, Object... args) {
        }

        @Override
        public void log(int level, String msg, Object... args) {
        }

        @Override
        public void setLevel(int level) {
        }

        @Override
        public Map<String, Object> getConfig() {
            return Map.of();
        }

        @Override
        public void reconfigure(Map<String, Object> config) {
        }
    }
}
