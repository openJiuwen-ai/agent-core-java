/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Spawn a teammate as an in-process coroutine (async task).
 * <p>
 * Mirrors the subprocess path (Runner.spawnAgent -> childProcess) but
 * runs everything within the same thread pool.
 * <p>
 * Mirrors Python's {@code inprocess_spawn} in
 * {@code openjiuwen.agent_teams.spawn.inprocess_spawn}.
 */
public class InProcessSpawn {

    private static final Logger logger = Logger.getLogger(InProcessSpawn.class.getName());

    /**
     * Spawn a teammate TeamAgent as a coroutine in the current process.
     *
     * @param teamAgent       The leader TeamAgent that owns the team spec
     * @param ctx             Runtime context for the teammate
     * @param initialMessage  First query to send to the teammate
     * @param sessionId       Session id to propagate
     * @return An InProcessHandle wrapping the teammate's CompletableFuture
     */
    public static InProcessHandle inprocessSpawn(
            Object teamAgent,
            Object ctx,
            String initialMessage,
            String sessionId) {

        TeamAgent leader = requireTeamAgent(teamAgent);
        TeamRuntimeContext runtimeContext = requireRuntimeContext(ctx);
        TeamAgentSpec spec = getSpec(leader);
        String teamName = getTeamName(spec, runtimeContext);
        String memberName = getMemberName(runtimeContext);
        String cardId = memberName != null ? teamName + "_" + memberName : "unknown";

        DeepAgentSpec agentSpec = getAgentSpec(spec, runtimeContext);
        AgentCard card = getOrCreateCard(agentSpec, cardId, memberName, runtimeContext);
        ensureMemberRuntime(leader, card, runtimeContext);

        String query = initialMessage != null ?
            initialMessage :
            "Join the team and wait for your first assignment.";

        CompletableFuture<Object> task = CompletableFuture.supplyAsync(() -> {
            return runWithSessionId(sessionId, () -> {
                logger.info("[inprocess] teammate " + memberName + " started");
                try {
                    return runAgentTeam(leader, memberName, query);
                } catch (RuntimeException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.info("[inprocess] teammate " + memberName + " cancelled");
                    } else {
                        logger.severe("[inprocess] teammate " + memberName + " crashed: " + e.getMessage());
                    }
                    throw e;
                }
            });
        });

        InProcessHandle handle = new InProcessHandle(
            "inproc-" + memberName,
            task
        );
        
        logger.info("[inprocess] spawned teammate " + memberName + " as task " + handle.getProcessId());
        return handle;
    }

    /**
     * Spawn with default initial message.
     */
    public static InProcessHandle inprocessSpawn(Object teamAgent, Object ctx, String sessionId) {
        return inprocessSpawn(teamAgent, ctx, null, sessionId);
    }

    /**
     * Spawn with default session id.
     */
    public static InProcessHandle inprocessSpawn(Object teamAgent, Object ctx) {
        return inprocessSpawn(teamAgent, ctx, null, null);
    }

    // ------------------------------------------------------------------
    // Typed helper methods
    // ------------------------------------------------------------------

    private static TeamAgent requireTeamAgent(Object teamAgent) {
        if (teamAgent instanceof TeamAgent typed) {
            return typed;
        }
        throw new IllegalArgumentException("teamAgent must be a TeamAgent");
    }

    private static TeamRuntimeContext requireRuntimeContext(Object ctx) {
        if (ctx instanceof TeamRuntimeContext typed) {
            return typed;
        }
        throw new IllegalArgumentException("ctx must be a TeamRuntimeContext");
    }

    private static TeamAgentSpec getSpec(TeamAgent teamAgent) {
        TeamAgentSpec spec = teamAgent.getSpec();
        if (spec == null) {
            throw new IllegalStateException("TeamAgent is not configured");
        }
        return spec;
    }

    private static String getTeamName(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        if (ctx.getTeamSpec() != null && ctx.getTeamSpec().getTeamName() != null
                && !ctx.getTeamSpec().getTeamName().isBlank()) {
            return ctx.getTeamSpec().getTeamName();
        }
        if (spec.getTeamName() != null && !spec.getTeamName().isBlank()) {
            return spec.getTeamName();
        }
        return "unknown";
    }

    private static String getMemberName(TeamRuntimeContext ctx) {
        String memberName = ctx.getMemberName();
        return memberName != null && !memberName.isBlank() ? memberName : "teammate";
    }

    private static DeepAgentSpec getAgentSpec(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        Map<String, DeepAgentSpec> agents = spec.getAgents();
        if (agents == null || agents.isEmpty()) {
            return null;
        }
        String roleKey = ctx.getRole() != null ? ctx.getRole().name().toLowerCase() : null;
        DeepAgentSpec agentSpec = roleKey != null ? agents.get(roleKey) : null;
        return agentSpec != null ? agentSpec : agents.get("leader");
    }

    private static AgentCard getOrCreateCard(
            DeepAgentSpec agentSpec,
            String cardId,
            String memberName,
            TeamRuntimeContext ctx) {
        AgentCard card = agentSpec != null && agentSpec.getConfig() != null
                ? agentSpec.getConfig().getCard()
                : null;
        if (card != null) {
            return card;
        }
        AgentCard generated = new AgentCard();
        generated.setId(cardId);
        generated.setName(memberName != null ? memberName : "unknown");
        String persona = ctx.getPersona();
        generated.setDescription(persona != null && !persona.isBlank() ? "Teammate: " + persona : "Teammate");
        return generated;
    }

    private static void ensureMemberRuntime(TeamAgent leader, AgentCard card, TeamRuntimeContext ctx) {
        String memberName = getMemberName(ctx);
        if (!leader.getTeamBackend().hasMember(memberName)) {
            leader.getTeamBackend().spawnMember(
                memberName,
                card.getName() != null ? card.getName() : memberName,
                card,
                ctx.getPersona(),
                null,
                com.openjiuwen.agent_teams.schema.status.MemberStatus.READY,
                com.openjiuwen.agent_teams.schema.status.ExecutionStatus.IDLE
            );
        } else if (leader.getTeamBackend().getMemberRuntime(memberName) == null) {
            leader.getTeamBackend().ensureMemberRuntime(memberName);
        }
    }

    private static Object runAgentTeam(TeamAgent leader, String memberName, String query) {
        return leader.runMember(memberName, query);
    }

    private interface ThrowingSupplier {
        Object get();
    }

    private static Object runWithSessionId(String sessionId, ThrowingSupplier supplier) {
        if (sessionId == null || sessionId.isBlank()) {
            return supplier.get();
        }
        String previous = SpawnContext.getSessionId();
        try {
            SpawnContext.setSessionId(sessionId);
            return supplier.get();
        } finally {
            if (previous == null || previous.isBlank()) {
                SpawnContext.resetSessionId();
            } else {
                SpawnContext.setSessionId(previous);
            }
        }
    }
}
