/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.session.Session;

import java.util.List;

/**
 * Session lifecycle and persistence helper for TeamAgent.
 *
 * <p>Mirrors Python's {@code SessionManager} in
 * {@code openjiuwen.agent_teams.agent.session_manager}.</p>
 */
public class SessionManager {

    private final TeamAgentSpecAccessor specAccessor;
    private final TeamBackendAccessor backendAccessor;
    private final RecoveryManager recoveryManager;

    private String sessionId;
    private Session teamSession;

    public SessionManager(
            TeamAgentSpecAccessor specAccessor,
            TeamBackendAccessor backendAccessor,
            RecoveryManager recoveryManager
    ) {
        this.specAccessor = specAccessor;
        this.backendAccessor = backendAccessor;
        this.recoveryManager = recoveryManager;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Session getTeamSession() {
        return teamSession;
    }

    public void registerCurrentSession(Session session) {
        bindSession(session);
        if (isPersistentLeader()) {
            recoveryManager.persistLeaderConfig(session);
        }
    }

    public void resumeForNewSession(Session session) {
        List<RecoveryManager.RecoverableMember> recoverableMembers = recoveryManager.collectLiveTeammatesForSessionSwitch();
        registerCurrentSession(session);
        if (!isPersistentLeader()) {
            return;
        }
        recoveryManager.restartForSessionSwitch(recoverableMembers, true);
        persistRecoverableMembers();
    }

    public void recoverForExistingSession(Session session) {
        bindSession(session);
        if (!isPersistentLeader()) {
            return;
        }
        recoveryManager.restoreLeaderConfig(session);
        recoveryManager.restoreRecoverableMembers(session.getState(RecoveryManager.RECOVERABLE_MEMBERS_KEY));
        recoveryManager.persistLeaderConfig(session);
        persistRecoverableMembers();
    }

    private void bindSession(Session session) {
        if (session == null) {
            return;
        }
        this.sessionId = session.getSessionId();
        this.teamSession = session;
    }

    private void persistRecoverableMembers() {
        if (teamSession == null) {
            return;
        }
        teamSession.updateState(java.util.Map.of(
                RecoveryManager.RECOVERABLE_MEMBERS_KEY,
                recoveryManager.snapshotRecoverableMembers()
        ));
    }

    private boolean isPersistentLeader() {
        if (specAccessor == null || specAccessor.getLifecycle() != TeamLifecycle.PERSISTENT) {
            return false;
        }
        TeamBackend backend = backendAccessor != null ? backendAccessor.getTeamBackend() : null;
        return backend == null || backend.isLeader();
    }

    @FunctionalInterface
    public interface TeamAgentSpecAccessor {
        TeamLifecycle getLifecycle();
    }

    @FunctionalInterface
    public interface TeamBackendAccessor {
        TeamBackend getTeamBackend();
    }
}
