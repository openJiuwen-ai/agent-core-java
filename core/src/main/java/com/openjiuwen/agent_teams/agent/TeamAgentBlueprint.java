/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import java.util.Objects;

/**
 * Immutable assembly blueprint for a TeamAgent.
 *
 * <p>Mirrors Python's {@code TeamAgentBlueprint} in
 * {@code openjiuwen/agent_teams/agent/blueprint.py}.</p>
 *
 * <p>All fields are determined when the blueprint is built and remain read-only
 * for the lifetime of the agent. Mutable runtime state and resources live
 * outside this value object.</p>
 */
public final class TeamAgentBlueprint {

    private final AgentCard card;
    private final TeamAgentSpec spec;
    private final TeamRuntimeContext ctx;
    private final String rolePolicy;
    private final String language;

    public TeamAgentBlueprint(
            AgentCard card,
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String rolePolicy,
            String language
    ) {
        this.card = Objects.requireNonNull(card, "card");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.rolePolicy = Objects.requireNonNullElse(rolePolicy, "");
        this.language = Objects.requireNonNullElse(language, "");
    }

    public AgentCard getCard() {
        return card;
    }

    public TeamAgentSpec getSpec() {
        return spec;
    }

    public TeamRuntimeContext getCtx() {
        return ctx;
    }

    public String getRolePolicy() {
        return rolePolicy;
    }

    public String getLanguage() {
        return language;
    }

    public TeamRole getRole() {
        return ctx.getRole();
    }

    public String getMemberName() {
        return ctx.getMemberName();
    }

    public String getLifecycle() {
        return spec.getLifecycle();
    }

    public TeamSpec getTeamSpec() {
        return ctx.getTeamSpec();
    }
}
