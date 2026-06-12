/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.SessionManagerView;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata.SessionStateAccess;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Manages TeamAgent session lifecycle and persistence.
 *
 * <p>Mirrors Python's {@code SessionManager} in
 * {@code openjiuwen/agent_teams/agent/session_manager.py}.</p>
 */
public class SessionManager implements SessionManagerView {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final TeamAgentStateView state;
    private final AgentConfigurator configurator;
    private final RecoveryManager recoveryManager;
    private AgentTeamsContext.SessionIdToken sessionIdToken;

    public SessionManager(
            TeamAgentStateView state,
            AgentConfigurator configurator,
            RecoveryManager recoveryManager
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.configurator = Objects.requireNonNull(configurator, "configurator");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager");
    }

    public AgentTeamSessionView getTeamSession() {
        return state.getTeamSession();
    }

    public void setTeamSession(AgentTeamSessionView session) {
        state.setTeamSession(session);
    }

    @Override
    public CompletionStage<Void> bindSession(Object session) {
        try {
            AgentTeamSessionView sessionView = requireSession(session);
            resetSessionIdToken();
            sessionIdToken = AgentTeamsContext.setSessionId(sessionView.getSessionId());
            state.setTeamSession(sessionView);
            return createCurrentSessionTables()
                    .thenRun(() -> persistLeaderConfigIfNeeded(sessionView));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void releaseSession() {
        resetSessionIdToken();
        state.setTeamSession(null);
    }

    public CompletionStage<Void> resumeForNewSession(AgentTeamSessionView session) {
        return recoveryManager.collectLiveTeammatesForSessionSwitch()
                .thenCompose(recoverableMembers -> bindSession(session)
                        .thenCompose(ignored -> restartForSessionSwitchIfLeader(recoverableMembers, true)));
    }

    public CompletionStage<Void> recoverForExistingSession(AgentTeamSessionView session) {
        return recoveryManager.collectLiveTeammatesForSessionSwitch()
                .thenCompose(recoverableMembers -> bindSession(session)
                        .thenCompose(ignored -> restartForSessionSwitchIfLeader(recoverableMembers, false)));
    }

    private void resetSessionIdToken() {
        AgentTeamsContext.SessionIdToken token = sessionIdToken;
        sessionIdToken = null;
        if (token == null) {
            return;
        }
        try {
            AgentTeamsContext.resetSessionId(token);
        } catch (RuntimeException exception) {
            TEAM_LOGGER.debug(
                    "session_id contextvar reset skipped (cross-context token): %s",
                    exception.getMessage()
            );
        }
    }

    private CompletionStage<Void> createCurrentSessionTables() {
        ConfiguredTeamBackend teamBackend = configurator.getTeamBackend();
        if (teamBackend instanceof SessionTableBackend sessionTableBackend) {
            CompletionStage<Void> result = sessionTableBackend.createCurSessionTables();
            return result == null ? CompletableFuture.completedFuture(null) : result;
        }
        return CompletableFuture.completedFuture(null);
    }

    private void persistLeaderConfigIfNeeded(AgentTeamSessionView session) {
        if (configurator.getSpec() != null && configurator.getRole() == TeamRole.LEADER) {
            recoveryManager.persistLeaderConfig(session);
        }
    }

    private CompletionStage<Void> restartForSessionSwitchIfLeader(
            List<RecoveryManager.RecoverableMember> recoverableMembers,
            boolean cleanupFirst
    ) {
        if (configurator.getRole() != TeamRole.LEADER || configurator.getTeamBackend() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return recoveryManager.restartForSessionSwitch(recoverableMembers, cleanupFirst);
    }

    private static AgentTeamSessionView requireSession(Object session) {
        if (session instanceof AgentTeamSessionView sessionView) {
            return sessionView;
        }
        throw new IllegalArgumentException("session must implement AgentTeamSessionView");
    }

    /**
     * Minimal session boundary used by the manager.
     *
     * <p>Mirrors Python's {@code AgentTeamSession} use in
     * {@code openjiuwen/agent_teams/agent/session_manager.py}.</p>
     */
    public interface AgentTeamSessionView extends SessionStateAccess {
        String getSessionId();
    }

    /**
     * Minimal state boundary used by the manager.
     *
     * <p>Mirrors Python's {@code TeamAgentState.team_session} access in
     * {@code openjiuwen/agent_teams/agent/session_manager.py}.</p>
     */
    public interface TeamAgentStateView {
        AgentTeamSessionView getTeamSession();

        void setTeamSession(AgentTeamSessionView session);
    }

    /**
     * Optional team backend hook for creating per-session DB tables.
     *
     * <p>Mirrors Python's {@code team_backend.db.create_cur_session_tables()} call in
     * {@code openjiuwen/agent_teams/agent/session_manager.py}.</p>
     */
    public interface SessionTableBackend {
        CompletionStage<Void> createCurSessionTables();
    }
}
