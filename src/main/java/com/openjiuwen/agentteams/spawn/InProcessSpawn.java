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
 *
 * <p>This intentionally keeps the first Java slice narrow: clone the team spec into a teammate host,
 * propagate session context, and run the teammate's first lifecycle entry point through the existing
 * {@link TeamAgent#dispatchTask(String)} surface.</p>
 *
 * @since 2026/7/9
 */
public final class InProcessSpawn {
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private InProcessSpawn() {
    }

    /**
     * Spawn a teammate as an in-process task on the given executor.
     *
     * <p>Creates a new {@link TeamAgent} configured with the parent's spec and the provided
     * runtime context, then submits it to the executor. The teammate runs its lifecycle
     * via {@link TeamAgent#invokeForSpawn(String)}. Session context is propagated via
     * {@link SpawnContext} so the teammate shares the same session as the leader.</p>
     *
     * @param teamAgent the parent team agent providing the team spec
     * @param ctx the runtime context for the new teammate (member name, etc.)
     * @param executor the shared executor service to run the teammate on
     * @param initialMessage optional initial message for the teammate; may be {@code null}
     * @param sessionId the session identifier to propagate; may be {@code null}
     * @return a handle containing the process ID and the submitted {@link Future}
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
        Loggers.AGENT.info("inprocessSpawn: creating teammate member={} sessionId={}",
                ctx.getMemberName(), sessionId);
        TeamAgent teammate = new TeamAgent().configure(teamAgent.getSpec(), ctx);

        String query = initialMessage != null ? initialMessage : "";

        Loggers.AGENT.info("inprocessSpawn: submitting teammate={} to executor, queryLen={}",
                ctx.getMemberName(), query.length());
        Future<?> task = executor.submit(() -> {
            Loggers.AGENT.info("inprocessSpawn: executor thread started for member={}", ctx.getMemberName());
            SpawnContext.SessionToken token = null;
            if (sessionId != null) {
                token = SpawnContext.setSessionId(sessionId);
            }
            try {
                // Mirrors Python inprocess_spawn.py: the teammate's TeamBackend
                // already shares the process-global database via TeamBackend.getSharedDb
                // (called inside the constructor), so no explicit shareDb step here.
                // invokeForSpawn blocks on the coordinator loop until shutdown_member
                // or clean_team stops it — no keep-alive sleep needed.
                teammate.invokeForSpawn(query);
                Loggers.AGENT.info("inprocessSpawn: member={} completed", ctx.getMemberName());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Loggers.AGENT.info("inprocessSpawn: interrupted for member={}", ctx.getMemberName());
            } catch (RuntimeException e) {
                // Safety net: invokeForSpawn may throw various runtime exceptions
                // (coordination kernel, stream controller, etc.); log and continue
                Loggers.AGENT.error("inprocessSpawn: error for member={}", ctx.getMemberName(), e);
            } finally {
                SpawnContext.resetSessionId(token);
            }
            return null;
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
