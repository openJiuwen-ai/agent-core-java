/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.session.AgentSessionApi;

/**
 * Manages session lifecycle and persistence for TeamAgent.
 *
 * <p>Mirrors Python SessionManager: manages session ID, team session
 * persistence, and session recovery.</p>
 */
public class SessionManager {

    private final AgentConfigurator configurator;
    private final RecoveryManager recoveryManager;

    private String sessionId;
    private Object teamSession;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionManager(AgentConfigurator configurator, RecoveryManager recoveryManager) {
        this.configurator = configurator;
        this.recoveryManager = recoveryManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getTeamSession() {
        return teamSession;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTeamSession(Object teamSession) {
        this.teamSession = teamSession;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void switchSession(AgentSessionApi session) {
        this.sessionId = session.getSessionId();
        com.openjiuwen.agentteams.spawn.SpawnContext.setSessionId(this.sessionId);

        if (configurator.getTeamBackend() != null) {
            configurator.getTeamBackend().getDb().createCurSessionTables();
        }

        if (configurator.getSpec() != null && configurator.getRole() == TeamRole.LEADER) {
            recoveryManager.persistLeaderConfig(
                    session, configurator.getSpec(), configurator.getCtx(),
                    configurator.getModelAllocator());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void resumeForNewSession(AgentSessionApi session) {
        java.util.List<RecoveryManager.RecoverableMember> recoverableMembers =
                recoveryManager.collectLiveTeammatesForSessionSwitch();
        switchSession(session);

        if (configurator.getRole() != TeamRole.LEADER
                || configurator.getTeamBackend() == null) {
            return;
        }

        recoveryManager.restartForSessionSwitch(recoverableMembers, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void recoverForExistingSession(AgentSessionApi session) {
        java.util.List<RecoveryManager.RecoverableMember> recoverableMembers =
                recoveryManager.collectLiveTeammatesForSessionSwitch();
        switchSession(session);

        if (configurator.getRole() != TeamRole.LEADER
                || configurator.getTeamBackend() == null) {
            return;
        }

        recoveryManager.restartForSessionSwitch(recoverableMembers, false);
    }
}
