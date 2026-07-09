/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Spawn a teammate as in-process work on a shared executor.
 * <p>
 * This intentionally keeps the first Java slice narrow: clone the team spec into a teammate host,
 * propagate session context, and run the teammate's first lifecycle entry point through the existing
 * {@link TeamAgent#dispatchTask(String)} surface.
 * </p>
 * 
 * @since 0.1.7
 */
public final class InProcessSpawn {
    /**
     * InProcessSpawn.
     * 
     * @since 0.1.7
     */
    private InProcessSpawn() {
    }

    /**
     * inprocessSpawn.
     * 
     * @param teamAgent teamAgent
     * @param ctx ctx
     * @param executor executor
     * @param initialMessage initialMessage
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static InProcessSpawnHandle inprocessSpawn(TeamAgent teamAgent, TeamRuntimeContext ctx,
            ExecutorService executor, String initialMessage, String sessionId) {
        Objects.requireNonNull(teamAgent, "teamAgent is required");
        Objects.requireNonNull(ctx, "ctx is required");
        Objects.requireNonNull(executor, "executor is required");

        TeamMemberSpec memberSpec = resolveMemberSpec(teamAgent, ctx);
        AgentCard card = buildCard(teamAgent, ctx, memberSpec);
        Loggers.AGENT.info("inprocessSpawn: creating teammate member={} sessionId={}", ctx.getMemberName(), sessionId);
        TeamAgent teammate = new TeamAgent().configure(teamAgent.getSpec(), ctx);

        String query = initialMessage != null && !initialMessage.isBlank()
                ? initialMessage
                : "Join the team and wait for your first assignment.";

        Loggers.AGENT.info("inprocessSpawn: submitting teammate={} to executor, query={}", ctx.getMemberName(), query);
        Future<?> task = executor.submit(() -> {
            Loggers.AGENT.info("inprocessSpawn: executor thread started for member={}", ctx.getMemberName());
            SpawnContext.SessionToken token = null;
            if (sessionId != null) {
                token = SpawnContext.setSessionId(sessionId);
            }
            try {
                // Share only the database so member sees the same tasks.
                // messageManager/members/taskManager are kept per-member for
                // correct identity (self-message filter, ownership checks).
                // Matches Python where each member has independent TeamBackend.
                teammate.getTeamBackend().shareDb(teamAgent.getTeamBackend());
                teammate.reregisterTeamTools();
                Loggers.AGENT.info("inprocessSpawn: shared leader state for member={}", ctx.getMemberName());
                // Match Python invoke(): process initial input through member's own ReAct stream
                String initialQuery = query != null ? query : "Join the team and wait for your first assignment.";
                // Match Python: invokeForSpawn blocks until the member's coordinator
                // loop is shut down (by shutdown_member or clean_team from the leader).
                teammate.invokeForSpawn(initialQuery);
                Loggers.AGENT.info("inprocessSpawn: member={} completed", ctx.getMemberName());
                // Keep thread alive so health check doesn't trigger restart
                while (true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Loggers.AGENT.info("inprocessSpawn: interrupted for member={}", ctx.getMemberName());
            } catch (Exception e) {
                Loggers.AGENT.error("inprocessSpawn: error for member={}", ctx.getMemberName(), e);
            } finally {
                SpawnContext.resetSessionId(token);
            }
            return null;
        });

        String processId =
            "inproc-" + (ctx.getMemberName() != null ? ctx.getMemberName() : UUID.randomUUID().toString());
        return InProcessSpawnHandle.builder().processId(processId).task(task).build();
    }

    /**
     * resolveMemberSpec.
     * 
     * @param teamAgent teamAgent
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private static TeamMemberSpec resolveMemberSpec(TeamAgent teamAgent, TeamRuntimeContext ctx) {
        if (teamAgent.getSpec() == null || teamAgent.getSpec().getMembers() == null) {
            return TeamMemberSpec.builder().name(ctx.getMemberName()).role(TeamRole.MEMBER).build();
        }
        return teamAgent.getSpec().getMembers().stream()
                .filter(member -> member != null && Objects.equals(member.getName(), ctx.getMemberName())).findFirst()
                .orElseGet(() -> teamAgent.getSpec().getMembers().stream()
                        .filter(member -> member != null && member.getRole() == TeamRole.LEADER).findFirst()
                        .orElseGet(() -> TeamMemberSpec.builder().name(ctx.getMemberName()).role(TeamRole.MEMBER)
                                .build()));
    }

    /**
     * buildCard.
     * 
     * @param teamAgent teamAgent
     * @param ctx ctx
     * @param memberSpec memberSpec
     * @return the result
     * @since 0.1.7
     */
    private static AgentCard buildCard(TeamAgent teamAgent, TeamRuntimeContext ctx, TeamMemberSpec memberSpec) {
        String teamName = teamAgent.getSpec() != null && teamAgent.getSpec().getName() != null
                ? teamAgent.getSpec().getName()
                : "default";
        String memberName = ctx.getMemberName() != null && !ctx.getMemberName().isBlank()
                ? ctx.getMemberName()
                : TeamConstants.DEFAULT_LEADER_MEMBER_NAME;
        return AgentCard.builder().id(teamName + "_" + memberName).name(memberName).description(
                memberSpec != null && memberSpec.getDescription() != null && !memberSpec.getDescription().isBlank()
                        ? memberSpec.getDescription()
                        : "Teammate")
                .build();
    }
}
