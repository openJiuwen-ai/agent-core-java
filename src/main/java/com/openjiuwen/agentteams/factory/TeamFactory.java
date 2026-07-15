/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.factory;

import com.openjiuwen.agentteams.agent.Allocation;
import com.openjiuwen.agentteams.agent.ModelAllocator;
import com.openjiuwen.agentteams.agent.ModelAllocators;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TeamFactory.
 * 
 * @since 0.1.7
 */
public final class TeamFactory {
    /**
     * TeamFactory.
     * 
     * @since 0.1.7
     */
    private TeamFactory() {
    }

    /**
     * createAgentTeam.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    public static TeamAgent createAgentTeam(TeamAgentSpec spec) {
        TeamAgentSpec builtSpec = spec.build();
        ModelAllocator allocator = ModelAllocators.buildModelAllocator(builtSpec);
        Allocation leaderAllocation = allocateLeaderModel(builtSpec, allocator);
        TeamRuntimeContext context = TeamRuntimeContext.builder().teamId(builtSpec.getName())
                .memberName(resolveLeader(builtSpec).getName()).metadata(new LinkedHashMap<>()).build();
        if (leaderAllocation != null) {
            context.getMetadata().put("member_model", leaderAllocation.toTeamModelConfig());
            context.getMetadata().put("leader_model_ref", leaderAllocation.toDbRef());
        }
        if (allocator != null) {
            context.getMetadata().put("model_allocator_state", new LinkedHashMap<>(allocator.stateDict()));
        }
        context.getMetadata().put("teammate_mode",
                builtSpec.getTeammateMode() != null ? builtSpec.getTeammateMode() : "build_mode");
        context.getMetadata().put("team_mode", builtSpec.getTeamMode() != null ? builtSpec.getTeamMode() : "default");
        context.getMetadata().put("enable_hitt", builtSpec.isEnableHitt());
        context.getMetadata().put("expose_human_agents_to_teammates", builtSpec.isExposeHumanAgentsToTeammates());
        if (builtSpec.getTransport() != null) {
            context.getMetadata().put("transport", builtSpec.getTransport());
        }
        context.getMetadata().put("storage", builtSpec.getStorage() != null ? builtSpec.getStorage() : "sqlite");
        if (builtSpec.getConnectionString() != null && !builtSpec.getConnectionString().isBlank()) {
            context.getMetadata().put("connection_string", builtSpec.getConnectionString());
        }
        return new TeamAgent().attachModelAllocator(allocator, leaderAllocation).configure(builtSpec, context);
    }

    /**
     * resumePersistentTeam.
     * 
     * @param agent agent
     * @param teamSessionId teamSessionId
     * @return the result
     * @since 0.1.7
     */
    public static TeamAgent resumePersistentTeam(TeamAgent agent, String teamSessionId) {
        return agent.resumeForNewSession(teamSessionId);
    }

    /**
     * recoverAgentTeam.
     * 
     * @param snapshot snapshot
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static TeamAgent recoverAgentTeam(Map<String, Object> snapshot) {
        Object rawSpec = snapshot.get("spec");
        if (!(rawSpec instanceof TeamAgentSpec spec)) {
            throw new IllegalArgumentException("snapshot.spec must be TeamAgentSpec");
        }
        Object rawContext = snapshot.get("context");
        if (!(rawContext instanceof TeamRuntimeContext context)) {
            throw new IllegalArgumentException("snapshot.context must be TeamRuntimeContext");
        }
        return new TeamAgent().configure(spec, context).restoreFromSnapshot(snapshot);
    }

    /**
     * allocateLeaderModel.
     * 
     * @param spec spec
     * @param allocator allocator
     * @return the result
     * @since 0.1.7
     */
    private static Allocation allocateLeaderModel(TeamAgentSpec spec, ModelAllocator allocator) {
        if (allocator == null || spec.getModelPool() == null || spec.getModelPool().isEmpty()) {
            return null;
        }
        TeamMemberSpec leader = resolveLeader(spec);
        Allocation allocation = allocator.allocate(leader.getModelName());
        if (allocation == null && requiresExplicitLeaderModel(spec)) {
            throw new IllegalArgumentException(
                    "leader.model_name must reference one of the configured model pool entries");
        }
        return allocation;
    }

    /**
     * requiresExplicitLeaderModel.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static boolean requiresExplicitLeaderModel(TeamAgentSpec spec) {
        return "by_model_name".equals(spec.getModelPoolStrategy()) && spec.getModelPool() != null
                && !spec.getModelPool().isEmpty();
    }

    /**
     * resolveLeader.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static TeamMemberSpec resolveLeader(TeamAgentSpec spec) {
        return spec.getMembers().stream().filter(member -> member.getRole() == TeamRole.LEADER).findFirst()
                .orElseGet(() -> TeamMemberSpec.builder()
                        .name(com.openjiuwen.agentteams.TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER)
                        .build());
    }
}
