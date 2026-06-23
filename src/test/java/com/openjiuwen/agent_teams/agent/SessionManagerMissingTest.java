/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_session_manager.py}.
 */
class SessionManagerMissingTest {

    private AgentTeamsContext.SessionIdToken isolationToken;

    @BeforeEach
    void isolateSessionIdContext() {
        isolationToken = AgentTeamsContext.setSessionId("");
    }

    @AfterEach
    void resetSessionIdContext() {
        AgentTeamsContext.resetSessionId(isolationToken);
    }

    @Test
    void bindSessionSetsContextvar() {
        SessionManager manager = makeManager();
        RecordingSession session = new RecordingSession("sess-A");

        assertThat(AgentTeamsContext.getSessionId()).isEmpty();
        manager.bindSession(session).toCompletableFuture().join();

        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("sess-A");
    }

    @Test
    void releaseSessionResetsContextvarAndDropsSession() {
        SessionManager manager = makeManager();
        RecordingSession session = new RecordingSession("sess-A");

        manager.bindSession(session).toCompletableFuture().join();
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("sess-A");

        manager.releaseSession();

        assertThat(AgentTeamsContext.getSessionId()).isEmpty();
        assertThat(manager.getTeamSession()).isNull();
    }

    @Test
    void rebindResetsPriorTokenThenReleaseReturnsToOuter() {
        SessionManager manager = makeManager();

        manager.bindSession(new RecordingSession("sess-A")).toCompletableFuture().join();
        manager.bindSession(new RecordingSession("sess-B")).toCompletableFuture().join();
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("sess-B");

        manager.releaseSession();

        assertThat(AgentTeamsContext.getSessionId()).isEmpty();
    }

    @Test
    void releaseAfterCrossContextBindDoesNotRaise() {
        SessionManager manager = makeManager();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> manager.bindSession(new RecordingSession("sess-X")).toCompletableFuture().join())
                    .get();

            assertThatCode(manager::releaseSession).doesNotThrowAnyException();
            assertThat(manager.getTeamSession()).isNull();
        } catch (Exception exception) {
            throw new AssertionError("cross-context bind setup failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static SessionManager makeManager() {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "desc"));
        return new SessionManager(new RecordingState(), configurator, new RecoveryManager(configurator, new NoopSpawnManager()));
    }

    private static final class RecordingState implements SessionManager.TeamAgentStateView {
        private SessionManager.AgentTeamSessionView teamSession;

        @Override
        public SessionManager.AgentTeamSessionView getTeamSession() {
            return teamSession;
        }

        @Override
        public void setTeamSession(SessionManager.AgentTeamSessionView session) {
            this.teamSession = session;
        }
    }

    private static final class RecordingSession implements SessionManager.AgentTeamSessionView {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static final class NoopSpawnManager implements RecoveryManager.SpawnManagerPort {
        @Override
        public java.util.concurrent.CompletionStage<Boolean> restartTeammate(String memberName) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> cleanupTeammate(String memberName) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public Map<String, Object> spawnedHandles() {
            return Map.of();
        }
    }
}
