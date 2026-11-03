/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.session.AgentSessionApi;

/**
 * Manages session lifecycle and persistence for TeamAgent.
 * <p>
 * Mirrors Python SessionManager: manages session ID, team session
 * persistence, and session recovery.
 * </p>
 * 
 * @since 0.1.7
 */
public class SessionManager {
    private final AgentConfigurator configurator;
    private final RecoveryManager recoveryManager;

    private String sessionId;

    /**
     * SessionManager.
     * 
     * @param configurator configurator
     * @param recoveryManager recoveryManager
     * @since 0.1.7
     */
    public SessionManager(AgentConfigurator configurator, RecoveryManager recoveryManager) {
        this.configurator = configurator;
        this.recoveryManager = recoveryManager;
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * setSessionId.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * switchSession.
     * 
     * @param session session
     * @since 0.1.7
     */
    public void switchSession(AgentSessionApi session) {
        this.sessionId = session.getSessionId();
        com.openjiuwen.agentteams.spawn.SpawnContext.setSessionId(this.sessionId);

        if (configurator.getTeamBackend() != null) {
            configurator.getTeamBackend().getDb().createCurSessionTables();
        }

        if (configurator.getSpec() != null && configurator.getRole() == TeamRole.LEADER) {
            recoveryManager.persistLeaderConfig(session, configurator.getSpec(), configurator.getCtx(),
                    configurator.getModelAllocator());
        }
    }

    /**
     * resumeForNewSession.
     * 
     * @param session session
     * @since 0.1.7
     */
    public void resumeForNewSession(AgentSessionApi session) {
        java.util.List<RecoveryManager.RecoverableMember> recoverableMembers =
            recoveryManager.collectLiveTeammatesForSessionSwitch();
        switchSession(session);

        if (configurator.getRole() != TeamRole.LEADER || configurator.getTeamBackend() == null) {
            return;
        }

        recoveryManager.restartForSessionSwitch(recoverableMembers, true);
    }

    /**
     * recoverForExistingSession.
     * 
     * @param session session
     * @since 0.1.7
     */
    public void recoverForExistingSession(AgentSessionApi session) {
        java.util.List<RecoveryManager.RecoverableMember> recoverableMembers =
            recoveryManager.collectLiveTeammatesForSessionSwitch();
        switchSession(session);

        if (configurator.getRole() != TeamRole.LEADER || configurator.getTeamBackend() == null) {
            return;
        }

        recoveryManager.restartForSessionSwitch(recoverableMembers, false);
    }
}
