/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Spawn a teammate as in-process work on a shared executor.
 *
 * <p>This intentionally keeps the first Java slice narrow: clone the team spec into a teammate host,
 * propagate session context, and run the teammate's first lifecycle entry point through the existing
 * {@link TeamAgent#dispatchTask(String)} surface.</p>
 */
public final class InProcessSpawn {
    private InProcessSpawn() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static InProcessSpawnHandle inprocessSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx,
            ExecutorService executor,
            String initialMessage,
            String sessionId
    ) {
        Objects.requireNonNull(teamAgent, "teamAgent is required");
        Objects.requireNonNull(ctx, "ctx is required");
        Objects.requireNonNull(executor, "executor is required");

        TeamMemberSpec memberSpec = resolveMemberSpec(teamAgent, ctx);
        AgentCard card = buildCard(teamAgent, ctx, memberSpec);
        TeamAgent teammate = new TeamAgent().configure(teamAgent.getSpec(), ctx);

        String query = initialMessage != null && !initialMessage.isBlank()
                ? initialMessage
                : "Join the team and wait for your first assignment.";

        Future<?> task = executor.submit(() -> {
            SpawnContext.SessionToken token = null;
            if (sessionId != null) {
                token = SpawnContext.setSessionId(sessionId);
            }
            try {
                return teammate.dispatchTask(query);
            } finally {
                SpawnContext.resetSessionId(token);
            }
        });

        String processId = "inproc-" + (ctx.getMemberName() != null
                ? ctx.getMemberName()
                : UUID.randomUUID().toString());
        return InProcessSpawnHandle.builder()
                .processId(processId)
                .task(task)
                .build();
    }

    private static TeamMemberSpec resolveMemberSpec(TeamAgent teamAgent, TeamRuntimeContext ctx) {
        if (teamAgent.getSpec() == null || teamAgent.getSpec().getMembers() == null) {
            return TeamMemberSpec.builder()
                    .name(ctx.getMemberName())
                    .role(TeamRole.MEMBER)
                    .build();
        }
        return teamAgent.getSpec().getMembers().stream()
                .filter(member -> member != null && Objects.equals(member.getName(), ctx.getMemberName()))
                .findFirst()
                .orElseGet(() -> teamAgent.getSpec().getMembers().stream()
                        .filter(member -> member != null && member.getRole() == TeamRole.LEADER)
                        .findFirst()
                        .orElseGet(() -> TeamMemberSpec.builder()
                                .name(ctx.getMemberName())
                                .role(TeamRole.MEMBER)
                                .build()));
    }

    private static AgentCard buildCard(TeamAgent teamAgent, TeamRuntimeContext ctx, TeamMemberSpec memberSpec) {
        String teamName = teamAgent.getSpec() != null && teamAgent.getSpec().getName() != null
                ? teamAgent.getSpec().getName()
                : "default";
        String memberName = ctx.getMemberName() != null && !ctx.getMemberName().isBlank()
                ? ctx.getMemberName()
                : TeamConstants.DEFAULT_LEADER_MEMBER_NAME;
        return AgentCard.builder()
                .id(teamName + "_" + memberName)
                .name(memberName)
                .description(memberSpec != null
                        && memberSpec.getDescription() != null
                        && !memberSpec.getDescription().isBlank()
                        ? memberSpec.getDescription()
                        : "Teammate")
                .build();
    }
}
