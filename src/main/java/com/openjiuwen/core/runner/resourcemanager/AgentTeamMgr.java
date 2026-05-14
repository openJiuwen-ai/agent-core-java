/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.function.Supplier;

/**
 * Manager for AgentTeam resource providers.
 * <p>
 * Mirrors Python's {@code AgentTeamMgr} in {@code resources_manager/agent_team_manager.py}.
 * <p>
 * Replaces the legacy {@link AgentGroupMgr} to align with the Python 0.1.12
 * team-oriented model (BaseTeam / TeamCard) replacing the old group model (BaseGroup / GroupCard).
 *
 * @param <T> the agent team base type
 */
public class AgentTeamMgr<T> extends AbstractManager<T> {

    /**
     * Register an agent team provider.
     *
     * @param agentTeamId the unique team identifier
     * @param agentTeam   the provider that supplies the team instance
     */
    public void addAgentTeam(String agentTeamId, Supplier<? extends T> agentTeam) {
        registerResourceProvider(agentTeamId, agentTeam);
    }

    /**
     * Unregister an agent team provider.
     *
     * @param agentTeamId the team identifier to remove
     * @return the removed provider, or null if not found
     */
    public Supplier<? extends T> removeAgentTeam(String agentTeamId) {
        return unregisterResourceProvider(agentTeamId);
    }

    /**
     * Get an agent team instance by its identifier.
     *
     * @param agentTeamId the team identifier
     * @return the team instance, or null if not registered
     */
    public T getAgentTeam(String agentTeamId) {
        return getResource(agentTeamId);
    }
}
