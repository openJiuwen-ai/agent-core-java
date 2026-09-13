/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.Optional;

/**
 * Immutable holder of blueprint-level config for a TeamAgent.
 *
 * <p>Mirrors Python {@code agent/blueprint.py:TeamAgentBlueprint}. Constructed
 * from the owning {@code TeamAgent}'s spec and context during
 * {@code CoordinationKernel.setup()}, then passed into the
 * {@code EventDispatcher} and its handlers. Handlers read from this object
 * rather than reaching back into the agent — the iron rule that keeps handlers
 * decoupled from the host.
 *
 * <p>Accessors return {@code Optional} wrappers because the context or spec
 * may be absent during early construction. Handlers that need raw values
 * can use {@code .orElse(null)} or {@code .orElseThrow()} at the call site.
 *
 * @since 2026/7/9
 */
public record TeamAgentBlueprint(
        TeamAgentSpec spec,
        TeamRuntimeContext ctx
) {

    /**
     * Return the role (leader / teammate / human_agent).
     *
     * @return an {@link Optional} containing the role, or empty when the context is missing
     */
    public Optional<TeamRole> role() {
        return ctx != null ? Optional.of(ctx.getRole()) : Optional.empty();
    }

    /**
     * Return the owning member name.
     *
     * @return an {@link Optional} containing the member name, or empty when the context is missing
     */
    public Optional<String> memberName() {
        return ctx != null ? Optional.of(ctx.getMemberName()) : Optional.empty();
    }

    /**
     * Return the team lifecycle hint ({@code "persistent"} / {@code "temporary"}).
     *
     * @return an {@link Optional} containing the lifecycle string, or empty when the spec is missing
     */
    public Optional<String> lifecycle() {
        return spec != null ? Optional.of(spec.getLifecycle()) : Optional.empty();
    }
}
