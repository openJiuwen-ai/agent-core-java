/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.RecoveryManager.RecoverableMember;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata.SessionStateAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link SessionManager}.
 *
 * <p>Mirrors Python's {@code SessionManager} in
 * {@code openjiuwen/agent_teams/agent/session_manager.py}.</p>
 */
class SessionManagerTest {

    @Test
    void bindSessionSetsContextStateTablesAndPersistsLeaderConfig() {
        AgentTeamsContext.SessionIdToken outerToken = AgentTeamsContext.setSessionId("outer");
        try {
            AgentConfigurator configurator = leaderConfigurator();
            RecordingBackend backend = new RecordingBackend("team", "leader");
            configurator.setTeamBackend(backend);
            RecordingRecoveryManager recoveryManager = new RecordingRecoveryManager(configurator);
            RecordingState state = new RecordingState();
            SessionManager manager = new SessionManager(state, configurator, recoveryManager);
            RecordingSession session = new RecordingSession("session-1");

            manager.bindSession(session).toCompletableFuture().join();

            assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-1");
            assertThat(state.getTeamSession()).isSameAs(session);
            assertThat(backend.createTablesCount).isEqualTo(1);
            assertThat(recoveryManager.persistCount).isEqualTo(1);

            manager.releaseSession();

            assertThat(AgentTeamsContext.getSessionId()).isEqualTo("outer");
            assertThat(state.getTeamSession()).isNull();
        } finally {
            AgentTeamsContext.resetSessionId(outerToken);
        }
    }

    @Test
    void rebindingResetsPreviousSessionTokenBeforeSettingNewSession() {
        AgentTeamsContext.SessionIdToken outerToken = AgentTeamsContext.setSessionId("outer");
        try {
            AgentConfigurator configurator = leaderConfigurator();
            RecordingRecoveryManager recoveryManager = new RecordingRecoveryManager(configurator);
            SessionManager manager = new SessionManager(new RecordingState(), configurator, recoveryManager);

            manager.bindSession(new RecordingSession("session-1")).toCompletableFuture().join();
            manager.bindSession(new RecordingSession("session-2")).toCompletableFuture().join();
            manager.releaseSession();

            assertThat(AgentTeamsContext.getSessionId()).isEqualTo("outer");
            assertThat(recoveryManager.persistCount).isEqualTo(2);
        } finally {
            AgentTeamsContext.resetSessionId(outerToken);
        }
    }

    @Test
    void resumeAndRecoverRestartLeaderTeammatesWithExpectedCleanupFlag() {
        AgentTeamsContext.SessionIdToken outerToken = AgentTeamsContext.setSessionId("outer");
        try {
            AgentConfigurator configurator = leaderConfigurator();
            configurator.setTeamBackend(new RecordingBackend("team", "leader"));
            RecordingRecoveryManager recoveryManager = new RecordingRecoveryManager(configurator);
            SessionManager manager = new SessionManager(new RecordingState(), configurator, recoveryManager);

            manager.resumeForNewSession(new RecordingSession("session-new")).toCompletableFuture().join();
            manager.recoverForExistingSession(new RecordingSession("session-old")).toCompletableFuture().join();

            assertThat(recoveryManager.collectCount).isEqualTo(2);
            assertThat(recoveryManager.cleanupFlags).containsExactly(true, false);
            assertThat(recoveryManager.restartMembers).containsExactly("dev", "dev");
            assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-old");
        } finally {
            AgentTeamsContext.resetSessionId(outerToken);
        }
    }

    @Test
    void nonLeaderBindSkipsSessionSwitchRestart() {
        AgentConfigurator configurator = teammateConfigurator();
        configurator.setTeamBackend(new RecordingBackend("team", "dev"));
        RecordingRecoveryManager recoveryManager = new RecordingRecoveryManager(configurator);
        SessionManager manager = new SessionManager(new RecordingState(), configurator, recoveryManager);

        manager.resumeForNewSession(new RecordingSession("session-1")).toCompletableFuture().join();

        assertThat(recoveryManager.collectCount).isEqualTo(1);
        assertThat(recoveryManager.cleanupFlags).isEmpty();
        manager.releaseSession();
    }

    private static AgentConfigurator leaderConfigurator() {
        return configurator(TeamRole.LEADER, "leader");
    }

    private static AgentConfigurator teammateConfigurator() {
        return configurator(TeamRole.TEAMMATE, "dev");
    }

    private static AgentConfigurator configurator(TeamRole role, String memberName) {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "desc"));
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setAgents(Map.of("leader", leader));
        spec.setTeamName("team");

        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        ctx.setTeamSpec(new TeamSpec("team", "Team", "leader"));

        configurator.setupInfra(spec, ctx);
        return configurator;
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

    private static final class RecordingBackend extends ConfiguredTeamBackend
            implements SessionManager.SessionTableBackend {
        private int createTablesCount;

        private RecordingBackend(String teamName, String memberName) {
            super(
                    teamName,
                    memberName,
                    "leader".equals(memberName),
                    Map.of(),
                    null,
                    "default",
                    List.of(),
                    null,
                    null,
                    false,
                    false,
                    List.of(),
                    null,
                    null,
                    "leader"
            );
        }

        @Override
        public CompletionStage<Void> createCurSessionTables() {
            createTablesCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingRecoveryManager extends RecoveryManager {
        private int persistCount;
        private int collectCount;
        private final List<Boolean> cleanupFlags = new ArrayList<>();
        private final List<String> restartMembers = new ArrayList<>();

        private RecordingRecoveryManager(AgentConfigurator configurator) {
            super(configurator, new NoopSpawnManager());
        }

        @Override
        public void persistLeaderConfig(SessionStateAccess session) {
            persistCount++;
            session.updateState(Map.of("persisted", true));
        }

        @Override
        public CompletionStage<List<RecoverableMember>> collectLiveTeammatesForSessionSwitch() {
            collectCount++;
            return CompletableFuture.completedFuture(
                    List.of(new RecoverableMember("dev", MemberStatus.READY))
            );
        }

        @Override
        public CompletionStage<Void> restartForSessionSwitch(
                List<RecoverableMember> recoverableMembers,
                boolean cleanupFirst
        ) {
            cleanupFlags.add(cleanupFirst);
            recoverableMembers.forEach(member -> restartMembers.add(member.memberName()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class NoopSpawnManager implements RecoveryManager.SpawnManagerPort {
        @Override
        public CompletionStage<Boolean> restartTeammate(String memberName) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Void> cleanupTeammate(String memberName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Map<String, Object> spawnedHandles() {
            return Map.of();
        }
    }
}
