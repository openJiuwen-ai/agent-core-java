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

/** Auto-generated for codecheck compliance. */
public final class TeamFactory {
  private TeamFactory() {}

  /** Auto-generated for codecheck compliance. */
  public static TeamAgent createAgentTeam(TeamAgentSpec spec) {
    TeamAgentSpec builtSpec = spec.build();
    ModelAllocator allocator = ModelAllocators.buildModelAllocator(builtSpec);
    Allocation leaderAllocation = allocateLeaderModel(builtSpec, allocator);
    TeamRuntimeContext context =
        TeamRuntimeContext.builder()
            .teamId(builtSpec.getName())
            .memberName(resolveLeader(builtSpec).getName())
            .metadata(new LinkedHashMap<>())
            .build();
    if (leaderAllocation != null) {
      context.getMetadata().put("member_model", leaderAllocation.toTeamModelConfig());
      context.getMetadata().put("leader_model_ref", leaderAllocation.toDbRef());
    }
    if (allocator != null) {
      context
          .getMetadata()
          .put("model_allocator_state", new LinkedHashMap<>(allocator.stateDict()));
    }
    return new TeamAgent()
        .attachModelAllocator(allocator, leaderAllocation)
        .configure(builtSpec, context);
  }

  /** Auto-generated for codecheck compliance. */
  public static TeamAgent resumePersistentTeam(TeamAgent agent, String teamSessionId) {
    return agent.resumeForNewSession(teamSessionId);
  }

  /** Auto-generated for codecheck compliance. */
  @SuppressWarnings("unchecked")
  /** Auto-generated for codecheck compliance. */
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

  private static boolean requiresExplicitLeaderModel(TeamAgentSpec spec) {
    return "by_model_name".equals(spec.getModelPoolStrategy())
        && spec.getModelPool() != null
        && !spec.getModelPool().isEmpty();
  }

  private static TeamMemberSpec resolveLeader(TeamAgentSpec spec) {
    return spec.getMembers().stream()
        .filter(member -> member.getRole() == TeamRole.LEADER)
        .findFirst()
        .orElseGet(
            () ->
                TeamMemberSpec.builder()
                    .name(com.openjiuwen.agentteams.TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                    .role(TeamRole.LEADER)
                    .build());
  }
}
